# Aura Diffusion • Uncensored Mobile AI Image Generation

<p align="center">
  <b>High-Power, Unrestricted On-Device AI Diffusion Engine</b><br>
  Tailor-engineered for <b>Qualcomm Snapdragon 8 Gen 3 (Samsung Galaxy S24)</b> with Adreno 750 Vulkan acceleration.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/SoC-Snapdragon_8_Gen_3-E01E37?style=for-the-badge&logo=qualcomm&logoColor=white" alt="SoC" />
  <img src="https://img.shields.io/badge/GPU-Adreno_750_Vulkan_1.3-00E5FF?style=for-the-badge" alt="Vulkan" />
  <img src="https://img.shields.io/badge/Model-GGUF_Quantized-A855F7?style=for-the-badge" alt="Model" />
  <img src="https://img.shields.io/badge/Content-100%25_Uncensored-10B981?style=for-the-badge" alt="Uncensored" />
</p>

---

## 🌟 Key Highlights

* **100% On-Device & Offline:** Synthesizes photorealistic images directly on your mobile hardware. Zero cloud dependencies, zero external telemetry, zero prompt logging.
* **Completely Uncensored & Unbound:** No corporate blacklists, safety filters, or refused prompts. Every prompt is generated faithfully.
* **Dual Synthesis Modes:**
  * **Text-to-Image (`txt2img`):** Generate high-fidelity art and photorealistic portraits from detailed prompts.
  * **Image-to-Image (`img2img`):** Transform any photo from Samsung Gallery using prompt guidance and configurable denoising strength. Direct integration with Android's "Share via..." menu.
* **Anti-Deformation Anatomy Guard:**
  * **No Extra Limbs:** Enforces native latent dimensions (512×512, 512×768) to prevent UNet tiling and torso/arm duplication.
  * **Auto-Negative Injection:** Injects master anatomical negative tokens into every prompt to eliminate bad hands, missing fingers, and warped proportions.
  * **2nd-Pass Face Refiner:** Detects faces and performs a localized re-denoise pass for symmetrical, razor-sharp eyes and realistic skin textures.
* **Strict Minimal Permissions:**
  * Strictly requires only `INTERNET` (for one-click model downloads) and modern **Scoped Storage / Photo Picker** (zero invasive permission prompts, direct export to `Pictures/AuraDiffusion`).

---

## ⚡ Hardware Profile & Optimization (Samsung Galaxy S24)

| Spec Component | Device Hardware | Our Engineering Optimization |
| :--- | :--- | :--- |
| **SoC** | Snapdragon 8 Gen 3 for Galaxy | ARM64-v8a native compilation with Cortex-X4 / A720 vector optimizations. |
| **GPU Compute** | Adreno 750 (Vulkan 1.3) | Hardware-accelerated Vulkan compute via `stable-diffusion.cpp` engine. |
| **RAM Budget** | 8 GB LPDDR5X | Peak VRAM strictly capped at **1.2 GB – 1.6 GB** using GGUF Q4_0 quantization and VAE tiling. Eliminates Android Low Memory Killer (LMK) crashes. |
| **Storage** | 256 GB UFS 4.0 | Fast model weight streaming directly from app-private storage. |
| **Display** | 120Hz Dynamic AMOLED 2X | Decoupled background coroutines ensure the Jetpack Compose UI stays at a fluid 120 FPS. |

---

## 📥 Direct Installation

1. Sideload the release APK directly to your Galaxy S24:
   ```bash
   adb install -r release/AuraDiffusion_v1.0_Release.apk
   ```
2. Open **Aura Diffusion** from your app drawer.
3. Switch to the **Models** tab and tap **Download** on *Realistic Vision v5.1 (Q4_0)* (or tap **Import .GGUF** to select any model from your phone storage).
4. Enter your prompt and tap **Synthesize Artwork**!

---

## 🛠️ Project Architecture

```
AuraDiffusion/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Minimal permissions & Foreground Service
│   │   ├── jniLibs/arm64-v8a/
│   │   │   ├── libomp.so                # OpenMP runtime
│   │   │   ├── libstable-diffusion.so   # CPU fallback
│   │   │   ├── libstable-diffusion_opencl.so
│   │   │   └── libstable-diffusion_vulkan.so  # Adreno 750 Vulkan engine
│   │   └── java/com/auradiffusion/app/
│   │       ├── MainActivity.kt          # Bottom navigation & Share intent handler
│   │       ├── core/
│   │       │   ├── NativeSD.kt          # JNA bridge to stable-diffusion.cpp
│   │       │   ├── DiffusionEngine.kt   # Generation pipeline & 2-pass Face Refiner
│   │       │   ├── AnatomyGuard.kt      # Anti-deformation & aspect ratio lock
│   │       │   └── ModelManager.kt      # GGUF download & MediaStore gallery export
│   │       ├── service/
│   │       │   └── GenerationForegroundService.kt  # WakeLock & LMK crash protection
│   │       └── ui/
│   │           ├── theme/               # Obsidian dark crystal theme
│   │           └── screens/             # Txt2Img, Img2Img, ModelHub, Gallery
└── release/
    └── AuraDiffusion_v1.0_Release.apk   # Signed production APK
```

---

## 📜 License

Licensed under the [Apache License, Version 2.0](LICENSE).
