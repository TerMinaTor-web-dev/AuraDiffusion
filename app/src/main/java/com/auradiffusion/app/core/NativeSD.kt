package com.auradiffusion.app.core

import android.content.Context
import android.util.Log
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Native JNA interface directly binding to leejet/stable-diffusion.cpp
 * running on Adreno 750 Vulkan GPU engine with automatic CPU fallback.
 */
object NativeSD {
    private const val TAG = "AuraNativeSD"

    @Structure.FieldOrder("width", "height", "channel", "data")
    open class SDImage : Structure {
        @JvmField var width: Int = 0
        @JvmField var height: Int = 0
        @JvmField var channel: Int = 0
        @JvmField var data: Pointer? = null

        constructor() : super()
        constructor(p: Pointer) : super(p) { read() }

        class ByValue : SDImage(), Structure.ByValue
    }

    interface SDProgressCallback : Callback {
        fun invoke(step: Int, steps: Int, time: Float, data: Pointer?)
    }

    interface SDLibrary : Library {
        fun get_num_physical_cores(): Int
        fun sd_set_progress_callback(cb: SDProgressCallback?, data: Pointer?)

        fun new_sd_ctx(
            model_path: String,
            vae_path: String,
            taesd_path: String,
            control_net_path: String,
            lora_model_dir: String,
            embedding_dir: String,
            id_embeddings_path: String,
            vae_decode_only: Boolean,
            vae_tiling: Boolean,
            free_params_immediately: Boolean,
            n_threads: Int,
            wtype: Int,
            rng_type: Int,
            schedule: Int,
            keep_clip_on_cpu: Boolean,
            keep_control_net_cpu: Boolean,
            keep_vae_on_cpu: Boolean
        ): Pointer?

        fun free_sd_ctx(ctx: Pointer?)

        fun txt2img(
            ctx: Pointer?,
            prompt: String,
            negative_prompt: String,
            clip_skip: Int,
            cfg_scale: Float,
            guidance: Float,
            width: Int,
            height: Int,
            sample_method: Int,
            sample_steps: Int,
            seed: Long,
            batch_count: Int,
            control_cond: Pointer?,
            control_strength: Float,
            style_strength: Float,
            normalize_input: Boolean,
            input_id_images_path: String
        ): Pointer?

        fun img2img(
            ctx: Pointer?,
            init_image: SDImage.ByValue,
            mask_image: SDImage.ByValue,
            prompt: String,
            negative_prompt: String,
            clip_skip: Int,
            cfg_scale: Float,
            guidance: Float,
            width: Int,
            height: Int,
            sample_method: Int,
            sample_steps: Int,
            strength: Float,
            seed: Long,
            batch_count: Int,
            control_cond: Pointer?,
            control_strength: Float,
            style_strength: Float,
            normalize_input: Boolean,
            input_id_images_path: String
        ): Pointer?
    }

    private var libInstance: SDLibrary? = null
    var activeBackend: String = "Unloaded"
        private set

    /**
     * Ensures native libraries (.so) are available on disk and JNA is initialized.
     */
    private fun ensureNativeLibs(context: Context?): File? {
        if (context == null) return null

        val appNativeDir = File(context.applicationInfo.nativeLibraryDir)
        val jniDispatchFile = File(appNativeDir, "libjnidispatch.so")
        val sdFile = File(appNativeDir, "libstable-diffusion.so")

        if (jniDispatchFile.exists() && sdFile.exists()) {
            return appNativeDir
        }

        // If OS did not extract them, extract directly from APK to private files dir
        val fallbackDir = File(context.filesDir, "lib_arm64").apply { mkdirs() }
        val targetSo = File(fallbackDir, "libstable-diffusion.so")
        if (targetSo.exists() && targetSo.length() > 1000000) {
            return fallbackDir
        }

        try {
            val apkPath = context.packageCodePath
            ZipFile(File(apkPath)).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("lib/arm64-v8a/") && entry.name.endsWith(".so")) {
                        val fileName = entry.name.substringAfterLast("/")
                        val destFile = File(fallbackDir, fileName)
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.i(TAG, "Extracted native lib: $fileName (${destFile.length()} bytes)")
                    }
                }
            }
            return fallbackDir
        } catch (e: Throwable) {
            Log.w(TAG, "Notice extracting native libs from APK: ${e.message}")
            return appNativeDir
        }
    }

    /**
     * Initializes the native library, prioritizing Vulkan acceleration on S24
     * with graceful fallback to OpenCL or CPU.
     */
    @Synchronized
    fun getLibrary(context: Context? = null): SDLibrary? {
        if (libInstance != null) return libInstance

        val nativeDir = ensureNativeLibs(context)
        if (nativeDir != null) {
            System.setProperty("jna.boot.library.path", nativeDir.absolutePath)
            System.setProperty("jna.library.path", nativeDir.absolutePath)
            System.setProperty("jna.nosys", "false")
        }

        // Preload JNA dispatcher and OpenMP
        try {
            if (nativeDir != null && File(nativeDir, "libjnidispatch.so").exists()) {
                System.load(File(nativeDir, "libjnidispatch.so").absolutePath)
            } else {
                System.loadLibrary("jnidispatch")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Notice loading jnidispatch: ${e.message}")
        }

        try {
            if (nativeDir != null && File(nativeDir, "libomp.so").exists()) {
                System.load(File(nativeDir, "libomp.so").absolutePath)
            } else {
                System.loadLibrary("omp")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Notice loading libomp: ${e.message}")
        }

        // 1. Try Vulkan (Snapdragon 8 Gen 3 Adreno 750 primary)
        try {
            val vulkanFile = if (nativeDir != null) File(nativeDir, "libstable-diffusion_vulkan.so") else null
            val lib = if (vulkanFile != null && vulkanFile.exists()) {
                try { System.load(vulkanFile.absolutePath) } catch (_: Throwable) {}
                Native.load(vulkanFile.absolutePath, SDLibrary::class.java)
            } else {
                try { System.loadLibrary("stable-diffusion_vulkan") } catch (_: Throwable) {}
                Native.load("stable-diffusion_vulkan", SDLibrary::class.java)
            }
            libInstance = lib
            activeBackend = "Vulkan (Adreno 750 GPU)"
            Log.i(TAG, "Vulkan backend loaded successfully!")
            return lib
        } catch (e: Throwable) {
            Log.w(TAG, "Vulkan load notice: ${e.message}, trying CPU fallback...")
        }

        // 2. Try CPU (Cortex-X4 / A720)
        try {
            val cpuFile = if (nativeDir != null) File(nativeDir, "libstable-diffusion.so") else null
            val lib = if (cpuFile != null && cpuFile.exists()) {
                try { System.load(cpuFile.absolutePath) } catch (_: Throwable) {}
                Native.load(cpuFile.absolutePath, SDLibrary::class.java)
            } else {
                try { System.loadLibrary("stable-diffusion") } catch (_: Throwable) {}
                Native.load("stable-diffusion", SDLibrary::class.java)
            }
            libInstance = lib
            activeBackend = "CPU (Cortex-X4 / A720)"
            Log.i(TAG, "CPU backend loaded successfully!")
            return lib
        } catch (e: Throwable) {
            Log.w(TAG, "CPU load notice: ${e.message}, trying OpenCL...")
        }

        // 3. Try OpenCL
        try {
            val oclFile = if (nativeDir != null) File(nativeDir, "libstable-diffusion_opencl.so") else null
            val lib = if (oclFile != null && oclFile.exists()) {
                try { System.load(oclFile.absolutePath) } catch (_: Throwable) {}
                Native.load(oclFile.absolutePath, SDLibrary::class.java)
            } else {
                try { System.loadLibrary("stable-diffusion_opencl") } catch (_: Throwable) {}
                Native.load("stable-diffusion_opencl", SDLibrary::class.java)
            }
            libInstance = lib
            activeBackend = "OpenCL GPU"
            Log.i(TAG, "OpenCL backend loaded successfully!")
            return lib
        } catch (e: Throwable) {
            Log.e(TAG, "All backend loads failed!", e)
        }

        return null
    }
}
