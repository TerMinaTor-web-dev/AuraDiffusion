package com.auradiffusion.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.FaceDetector
import android.util.Log
import com.sun.jna.Memory
import com.sun.jna.Pointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * High-level AI Image Generation Engine.
 * Handles lifecycle, model context caching, VRAM optimization,
 * progress broadcasting, and the two-pass face refiner.
 */
class DiffusionEngine(private val context: Context) {
    companion object {
        private const val TAG = "DiffusionEngine"
        @Volatile
        private var instance: DiffusionEngine? = null

        fun getInstance(context: Context): DiffusionEngine {
            return instance ?: synchronized(this) {
                instance ?: DiffusionEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private var currentCtx: Pointer? = null
    private var loadedModelPath: String? = null

    private val _progressState = MutableStateFlow(GenerationProgress(0, 0, 0f, "Idle"))
    val progressState: StateFlow<GenerationProgress> = _progressState.asStateFlow()

    data class GenerationProgress(
        val step: Int,
        val totalSteps: Int,
        val elapsedSec: Float,
        val statusMessage: String
    )

    data class GenerationConfig(
        val prompt: String,
        val negativePrompt: String = "",
        val width: Int = 512,
        val height: Int = 512,
        val steps: Int = 20,
        val cfgScale: Float = 6.5f,
        val seed: Long = -1L,
        val enableAnatomyGuard: Boolean = true,
        val enableAutoEnhance: Boolean = true,
        val enableFaceRefine: Boolean = true,
        val denoiseStrength: Float = 0.70f // For img2img
    )

    /**
     * Initializes or swaps the model context.
     * Keeps VAE tiling on to prevent VRAM spikes on Samsung S24.
     */
    suspend fun loadModel(modelFile: File): Boolean = withContext(Dispatchers.Default) {
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
            return@withContext false
        }

        if (currentCtx != null && loadedModelPath == modelFile.absolutePath) {
            return@withContext true // Already loaded
        }

        val lib = NativeSD.getLibrary() ?: return@withContext false

        // Free previous model to avoid OOM
        unloadModel()

        _progressState.value = GenerationProgress(0, 1, 0f, "Loading model weights into Vulkan VRAM...")

        try {
            val cores = try { lib.get_num_physical_cores().coerceAtLeast(4) } catch (_: Throwable) { 6 }
            
            // new_sd_ctx with vae_tiling = true (prevents 8GB RAM spikes)
            val ctx = lib.new_sd_ctx(
                model_path = modelFile.absolutePath,
                vae_path = "",
                taesd_path = "",
                control_net_path = "",
                lora_model_dir = "",
                embedding_dir = "",
                id_embeddings_path = "",
                vae_decode_only = false,
                vae_tiling = true, // Crucial for S24 zero-crash stability!
                free_params_immediately = false,
                n_threads = cores,
                wtype = 0, // NONE/Auto
                rng_type = 0, // STD_DEFAULT
                schedule = 0, // DEFAULT
                keep_clip_on_cpu = false,
                keep_control_net_cpu = false,
                keep_vae_on_cpu = false
            )

            if (ctx != null) {
                currentCtx = ctx
                loadedModelPath = modelFile.absolutePath
                _progressState.value = GenerationProgress(1, 1, 0f, "Model Ready (${NativeSD.activeBackend})")
                Log.i(TAG, "Model loaded successfully on ${NativeSD.activeBackend}")
                true
            } else {
                _progressState.value = GenerationProgress(0, 0, 0f, "Failed to load model context")
                false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error loading model context", e)
            _progressState.value = GenerationProgress(0, 0, 0f, "Load error: ${e.message}")
            false
        }
    }

    /**
     * Unloads model and reclaims native memory.
     */
    fun unloadModel() {
        val lib = NativeSD.getLibrary()
        currentCtx?.let {
            try {
                lib?.free_sd_ctx(it)
            } catch (e: Throwable) {
                Log.w(TAG, "Notice on free_sd_ctx: ${e.message}")
            }
        }
        currentCtx = null
        loadedModelPath = null
    }

    /**
     * Text-To-Image Generation.
     */
    suspend fun generateTxt2Img(config: GenerationConfig): Bitmap? = withContext(Dispatchers.Default) {
        val lib = NativeSD.getLibrary() ?: return@withContext null
        val ctx = currentCtx ?: return@withContext null

        val finalPositive = AnatomyGuard.enrichPositivePrompt(config.prompt, config.enableAutoEnhance)
        val finalNegative = AnatomyGuard.enrichNegativePrompt(config.negativePrompt, config.enableAnatomyGuard)
        val safeCfg = AnatomyGuard.getSafeGuidanceScale(config.cfgScale)
        val safeSteps = config.steps.coerceIn(4, 35)
        val seed = if (config.seed <= 0) System.currentTimeMillis() else config.seed

        // Register progress callback
        val callback = object : NativeSD.SDProgressCallback {
            override fun invoke(step: Int, steps: Int, time: Float, data: Pointer?) {
                _progressState.value = GenerationProgress(
                    step = step,
                    totalSteps = steps,
                    elapsedSec = time,
                    statusMessage = "Synthesizing latent canvas ($step/$steps steps)..."
                )
            }
        }
        lib.sd_set_progress_callback(callback, null)

        _progressState.value = GenerationProgress(0, safeSteps, 0f, "Initiating Euler/Adreno 750 pipeline...")

        try {
            val imgPtr = lib.txt2img(
                ctx = ctx,
                prompt = finalPositive,
                negative_prompt = finalNegative,
                clip_skip = -1,
                cfg_scale = safeCfg,
                guidance = 3.5f,
                width = config.width,
                height = config.height,
                sample_method = 0, // EULER_A
                sample_steps = safeSteps,
                seed = seed,
                batch_count = 1,
                control_cond = null,
                control_strength = 0.9f,
                style_strength = 20f,
                normalize_input = false,
                input_id_images_path = ""
            )

            if (imgPtr != null) {
                val rawImage = NativeSD.SDImage(imgPtr)
                var bitmap = convertSdImageToBitmap(rawImage)

                // Optional Pass 2: Face Refiner if requested
                if (bitmap != null && config.enableFaceRefine) {
                    _progressState.value = GenerationProgress(safeSteps, safeSteps, 0f, "Refining facial features...")
                    bitmap = refineFacesIfPresent(bitmap, config, lib, ctx)
                }

                _progressState.value = GenerationProgress(safeSteps, safeSteps, 0f, "Complete!")
                bitmap
            } else {
                _progressState.value = GenerationProgress(0, 0, 0f, "Generation returned null buffer")
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in txt2img", e)
            _progressState.value = GenerationProgress(0, 0, 0f, "Generation failed: ${e.message}")
            null
        } finally {
            lib.sd_set_progress_callback(null, null)
        }
    }

    /**
     * Image-To-Image Generation (User image + Prompt -> Transformed Art).
     */
    suspend fun generateImg2Img(initBitmap: Bitmap, config: GenerationConfig): Bitmap? = withContext(Dispatchers.Default) {
        val lib = NativeSD.getLibrary() ?: return@withContext null
        val ctx = currentCtx ?: return@withContext null

        val finalPositive = AnatomyGuard.enrichPositivePrompt(config.prompt, config.enableAutoEnhance)
        val finalNegative = AnatomyGuard.enrichNegativePrompt(config.negativePrompt, config.enableAnatomyGuard)
        val safeCfg = AnatomyGuard.getSafeGuidanceScale(config.cfgScale)
        val safeSteps = config.steps.coerceIn(4, 35)
        val seed = if (config.seed <= 0) System.currentTimeMillis() else config.seed

        // Scale bitmap to target resolution if needed
        val scaledBitmap = if (initBitmap.width != config.width || initBitmap.height != config.height) {
            Bitmap.createScaledBitmap(initBitmap, config.width, config.height, true)
        } else {
            initBitmap
        }

        val initSdImage = convertBitmapToSdImage(scaledBitmap)
        val emptyMask = NativeSD.SDImage.ByValue().apply {
            width = 0
            height = 0
            channel = 0
            data = null
        }

        val callback = object : NativeSD.SDProgressCallback {
            override fun invoke(step: Int, steps: Int, time: Float, data: Pointer?) {
                _progressState.value = GenerationProgress(
                    step = step,
                    totalSteps = steps,
                    elapsedSec = time,
                    statusMessage = "Transforming source image ($step/$steps)..."
                )
            }
        }
        lib.sd_set_progress_callback(callback, null)

        try {
            val imgPtr = lib.img2img(
                ctx = ctx,
                init_image = initSdImage,
                mask_image = emptyMask,
                prompt = finalPositive,
                negative_prompt = finalNegative,
                clip_skip = -1,
                cfg_scale = safeCfg,
                guidance = 3.5f,
                width = config.width,
                height = config.height,
                sample_method = 0, // EULER_A
                sample_steps = safeSteps,
                strength = config.denoiseStrength.coerceIn(0.1f, 0.95f),
                seed = seed,
                batch_count = 1,
                control_cond = null,
                control_strength = 0.9f,
                style_strength = 20f,
                normalize_input = false,
                input_id_images_path = ""
            )

            if (imgPtr != null) {
                val rawImage = NativeSD.SDImage(imgPtr)
                val bitmap = convertSdImageToBitmap(rawImage)
                _progressState.value = GenerationProgress(safeSteps, safeSteps, 0f, "Complete!")
                bitmap
            } else {
                _progressState.value = GenerationProgress(0, 0, 0f, "img2img returned null")
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in img2img", e)
            _progressState.value = GenerationProgress(0, 0, 0f, "img2img failed: ${e.message}")
            null
        } finally {
            lib.sd_set_progress_callback(null, null)
        }
    }

    /**
     * Converts native C raw RGB buffer to Android Bitmap.
     */
    private fun convertSdImageToBitmap(sdImage: NativeSD.SDImage): Bitmap? {
        val w = sdImage.width
        val h = sdImage.height
        val c = sdImage.channel
        val dataPtr = sdImage.data ?: return null

        if (w <= 0 || h <= 0 || (c != 3 && c != 4)) return null

        val pixelCount = w * h
        val byteCount = pixelCount * c
        val rawBytes = dataPtr.getByteArray(0, byteCount)

        val intPixels = IntArray(pixelCount)
        if (c == 3) {
            var byteIdx = 0
            for (i in 0 until pixelCount) {
                val r = rawBytes[byteIdx++].toInt() and 0xFF
                val g = rawBytes[byteIdx++].toInt() and 0xFF
                val b = rawBytes[byteIdx++].toInt() and 0xFF
                intPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        } else {
            var byteIdx = 0
            for (i in 0 until pixelCount) {
                val r = rawBytes[byteIdx++].toInt() and 0xFF
                val g = rawBytes[byteIdx++].toInt() and 0xFF
                val b = rawBytes[byteIdx++].toInt() and 0xFF
                val a = rawBytes[byteIdx++].toInt() and 0xFF
                intPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(intPixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    /**
     * Converts Android Bitmap to native SDImage struct for img2img.
     */
    private fun convertBitmapToSdImage(bitmap: Bitmap): NativeSD.SDImage.ByValue {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val byteSize = w * h * 3
        val memory = Memory(byteSize.toLong())
        var byteIdx = 0
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF).toByte()
            val g = (pixel shr 8 and 0xFF).toByte()
            val b = (pixel and 0xFF).toByte()
            memory.setByte((byteIdx++).toLong(), r)
            memory.setByte((byteIdx++).toLong(), g)
            memory.setByte((byteIdx++).toLong(), b)
        }

        return NativeSD.SDImage.ByValue().apply {
            this.width = w
            this.height = h
            this.channel = 3
            this.data = memory
        }
    }

    /**
     * 2nd-pass Face Refiner (ADetailer technique on mobile).
     * Detects face, crops, re-denoises lightly, and blends back.
     */
    private fun refineFacesIfPresent(
        sourceBitmap: Bitmap,
        config: GenerationConfig,
        lib: NativeSD.SDLibrary,
        ctx: Pointer
    ): Bitmap {
        try {
            // Android built-in FaceDetector expects Bitmap.Config.RGB_565
            val rgb565 = sourceBitmap.copy(Bitmap.Config.RGB_565, false)
            val detector = FaceDetector(rgb565.width, rgb565.height, 1)
            val faces = arrayOfNulls<FaceDetector.Face>(1)
            val count = detector.findFaces(rgb565, faces)

            if (count > 0 && faces[0] != null) {
                val face = faces[0]!!
                val midPoint = android.graphics.PointF()
                face.getMidPoint(midPoint)
                val eyeDist = face.eyesDistance()

                val faceBoxSize = (eyeDist * 3.0f).toInt().coerceIn(128, sourceBitmap.width.coerceAtMost(sourceBitmap.height))
                val left = (midPoint.x - faceBoxSize / 2).toInt().coerceIn(0, sourceBitmap.width - faceBoxSize)
                val top = (midPoint.y - faceBoxSize / 2).toInt().coerceIn(0, sourceBitmap.height - faceBoxSize)

                val faceCrop = Bitmap.createBitmap(sourceBitmap, left, top, faceBoxSize, faceBoxSize)
                val face512 = Bitmap.createScaledBitmap(faceCrop, 512, 512, true)

                // Refine face with low denoise strength (0.28)
                val faceSdImage = convertBitmapToSdImage(face512)
                val emptyMask = NativeSD.SDImage.ByValue()
                val facePrompt = "${config.prompt}, perfect face, detailed eyes, natural skin, sharp focus"
                val faceNegative = "deformed eyes, cross-eyed, blurry, bad anatomy"

                val refinedPtr = lib.img2img(
                    ctx = ctx,
                    init_image = faceSdImage,
                    mask_image = emptyMask,
                    prompt = facePrompt,
                    negative_prompt = faceNegative,
                    clip_skip = -1,
                    cfg_scale = 6.0f,
                    guidance = 3.5f,
                    width = 512,
                    height = 512,
                    sample_method = 0,
                    sample_steps = 10,
                    strength = 0.28f, // Subtle touchup
                    seed = System.currentTimeMillis(),
                    batch_count = 1,
                    control_cond = null,
                    control_strength = 0.9f,
                    style_strength = 20f,
                    normalize_input = false,
                    input_id_images_path = ""
                )

                if (refinedPtr != null) {
                    val refinedSd = NativeSD.SDImage(refinedPtr)
                    val refined512 = convertSdImageToBitmap(refinedSd)
                    if (refined512 != null) {
                        val refinedBack = Bitmap.createScaledBitmap(refined512, faceBoxSize, faceBoxSize, true)
                        val mutableSource = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
                        val canvas = Canvas(mutableSource)
                        canvas.drawBitmap(refinedBack, left.toFloat(), top.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG))
                        return mutableSource
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Notice on face refinement: ${e.message}")
        }
        return sourceBitmap
    }
}
