package com.auradiffusion.app.core

/**
 * AnatomyGuard: Anti-deformation and face consistency engine.
 * Specifically engineered to eliminate:
 * 1. Extra limbs (3 arms, multiple legs, duplicated torsos).
 * 2. Deformed, mutated, or missing fingers/hands.
 * 3. Face mismatch, asymmetrical eyes, and melted facial features.
 */
object AnatomyGuard {

    // Proven anatomical master negative keywords for Stable Diffusion 1.5 checkpoints
    val MASTER_ANATOMY_NEGATIVES = listOf(
        "extra limbs",
        "extra arms",
        "extra legs",
        "missing arms",
        "missing legs",
        "floating limbs",
        "disconnected limbs",
        "malformed limbs",
        "mutated hands",
        "poorly drawn hands",
        "poorly drawn face",
        "disfigured",
        "deformed fingers",
        "fused fingers",
        "too many fingers",
        "duplicate",
        "multiple heads",
        "cloned face",
        "mutation",
        "gross proportions",
        "long neck",
        "body out of frame",
        "bad anatomy",
        "bad proportions",
        "bad eyes",
        "cross-eyed",
        "mismatched eyes",
        "blurry face",
        "lowres",
        "error",
        "worst quality",
        "low quality",
        "normal quality",
        "jpeg artifacts"
    )

    // Positive enhancers for photorealism & crisp facial features
    val MASTER_POSITIVE_ENHANCERS = listOf(
        "8k uhd",
        "high quality",
        "masterpiece",
        "highly detailed",
        "sharp focus",
        "natural skin texture",
        "photorealistic",
        "cinematic lighting"
    )

    /**
     * Resolves safe resolutions that preserve body continuity and prevent
     * UNet tiling duplication artifacts (which cause extra torsos/limbs).
     */
    enum class AspectRatio(val title: String, val width: Int, val height: Int, val description: String) {
        SQUARE_1_1("1:1 Square", 512, 512, "Best for portraits, profile icons, and single subjects"),
        PORTRAIT_S24("9:16 Portrait", 512, 768, "Optimized for Galaxy S24 screen without torso duplication"),
        LANDSCAPE_S24("16:9 Landscape", 768, 512, "Cinematic panoramic view, sceneries & backgrounds"),
        PHOTO_STANDARD("4:5 Photo", 512, 640, "Classic Instagram/photo print framing")
    }

    /**
     * Combines user prompt with photorealism tokens if enabled.
     */
    fun enrichPositivePrompt(userPrompt: String, autoEnhance: Boolean): String {
        val trimmed = userPrompt.trim()
        if (!autoEnhance || trimmed.isBlank()) return trimmed

        val additions = MASTER_POSITIVE_ENHANCERS.filter {
            !trimmed.contains(it, ignoreCase = true)
        }
        return if (additions.isEmpty()) trimmed else "$trimmed, ${additions.joinToString(", ")}"
    }

    /**
     * Generates a bulletproof negative prompt by injecting anatomy guards.
     */
    fun enrichNegativePrompt(userNegative: String, anatomyGuardEnabled: Boolean): String {
        val userTokens = userNegative.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (!anatomyGuardEnabled) {
            return userTokens.joinToString(", ")
        }

        val combined = LinkedHashSet<String>()
        // User's custom negatives first
        combined.addAll(userTokens)
        // Add master anatomy protections
        combined.addAll(MASTER_ANATOMY_NEGATIVES)

        return combined.joinToString(", ")
    }

    /**
     * Clamps CFG scale within the safe sweet spot (5.0 - 7.5).
     * CFG above 8.0 burns images and leads to multi-limb artifacts.
     */
    fun getSafeGuidanceScale(cfg: Float): Float {
        return cfg.coerceIn(5.0f, 7.5f)
    }

    /**
     * Clamps generation steps for mobile Snapdragon 8 Gen 3 Adreno 750.
     * Prevents watchdog timeouts while ensuring convergence.
     */
    fun getOptimalSteps(steps: Int): Int {
        return steps.coerceIn(15, 30)
    }
}
