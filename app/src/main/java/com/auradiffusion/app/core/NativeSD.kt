package com.auradiffusion.app.core

import android.util.Log
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure

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
     * Initializes the native library, prioritizing Vulkan acceleration on S24
     * with graceful fallback to OpenCL or CPU.
     */
    @Synchronized
    fun getLibrary(): SDLibrary? {
        if (libInstance != null) return libInstance

        try {
            System.loadLibrary("omp")
        } catch (e: Throwable) {
            Log.w(TAG, "Notice on libomp load: ${e.message}")
        }

        // 1. Try Vulkan (Snapdragon 8 Gen 3 Adreno 750 primary)
        try {
            Log.i(TAG, "Loading libstable-diffusion_vulkan.so...")
            try { System.loadLibrary("stable-diffusion_vulkan") } catch (_: Throwable) {}
            val lib = Native.load("stable-diffusion_vulkan", SDLibrary::class.java)
            libInstance = lib
            activeBackend = "Vulkan (Adreno 750 GPU)"
            Log.i(TAG, "Vulkan backend loaded successfully!")
            return lib
        } catch (e: Throwable) {
            Log.w(TAG, "Vulkan load notice: ${e.message}, trying CPU fallback...")
        }

        // 2. Try CPU (Cortex-X4 / A720)
        try {
            Log.i(TAG, "Loading libstable-diffusion.so (CPU)...")
            try { System.loadLibrary("stable-diffusion") } catch (_: Throwable) {}
            val lib = Native.load("stable-diffusion", SDLibrary::class.java)
            libInstance = lib
            activeBackend = "CPU (Cortex-X4 / A720)"
            Log.i(TAG, "CPU backend loaded successfully!")
            return lib
        } catch (e: Throwable) {
            Log.w(TAG, "CPU load notice: ${e.message}, trying OpenCL...")
        }

        // 3. Try OpenCL
        try {
            try { System.loadLibrary("stable-diffusion_opencl") } catch (_: Throwable) {}
            val lib = Native.load("stable-diffusion_opencl", SDLibrary::class.java)
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
