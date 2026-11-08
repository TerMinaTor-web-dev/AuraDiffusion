package com.auradiffusion.app.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auradiffusion.app.core.AnatomyGuard
import com.auradiffusion.app.core.DiffusionEngine
import com.auradiffusion.app.core.ModelManager
import com.auradiffusion.app.service.GenerationForegroundService
import com.auradiffusion.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Txt2ImgScreen(
    engine: DiffusionEngine,
    modelManager: ModelManager,
    selectedModel: File?,
    onNavigateToModelHub: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var prompt by remember { mutableStateOf("Ultra-realistic photorealistic portrait of an athletic warrior, intricate details, natural skin texture, sharp symmetrical eyes, cinematic lighting") }
    var customNegative by remember { mutableStateOf("") }
    var selectedRatio by remember { mutableStateOf(AnatomyGuard.AspectRatio.PORTRAIT_S24) }
    var anatomyGuardEnabled by remember { mutableStateOf(true) }
    var autoEnhanceEnabled by remember { mutableStateOf(true) }
    var faceRefineEnabled by remember { mutableStateOf(true) }
    var steps by remember { mutableFloatStateOf(20f) }
    var cfgScale by remember { mutableFloatStateOf(6.5f) }
    var showAdvanced by remember { mutableStateOf(false) }

    var isGenerating by remember { mutableStateOf(false) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val progressState by engine.progressState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Model Status Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Active Model",
                        tint = AccentCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (selectedModel != null) selectedModel.nameWithoutExtension else "No Model Selected",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Snapdragon 8 Gen 3 • Adreno 750",
                            color = AccentEmerald,
                            fontSize = 11.sp
                        )
                    }
                }
                OutlinedButton(
                    onClick = onNavigateToModelHub,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan)
                ) {
                    Text("Change", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Prompt Input
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Creation Prompt", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = DarkSurfaceVariant,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Anatomy Guard Pill Banner (The core fix for extra limbs / face mismatch)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Anatomy Guard",
                        tint = if (anatomyGuardEnabled) AccentEmerald else TextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Anti-Deformation Guard",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Eliminates extra limbs, bad hands & face mismatch",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Switch(
                    checked = anatomyGuardEnabled,
                    onCheckedChange = { anatomyGuardEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentEmerald,
                        checkedTrackColor = DarkSurface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Aspect Ratio Selector
        Text("Canvas Ratio", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnatomyGuard.AspectRatio.values().forEach { ratio ->
                val isSelected = selectedRatio == ratio
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AccentCyan.copy(alpha = 0.2f) else DarkSurface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) AccentCyan else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedRatio = ratio }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = ratio.title.split(" ")[0],
                            color = if (isSelected) AccentCyan else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${ratio.width}x${ratio.height}",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Advanced Settings Accordion
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvanced = !showAdvanced }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Advanced Tuning & Face Refiner", color = TextSecondary, fontSize = 13.sp)
            Icon(
                imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "Expand",
                tint = TextSecondary
            )
        }

        AnimatedVisibility(visible = showAdvanced) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                // Face Refine Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("2nd-Pass Face Refiner", color = TextPrimary, fontSize = 13.sp)
                        Text("Re-denoises detected faces for sharp eyes", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(checked = faceRefineEnabled, onCheckedChange = { faceRefineEnabled = it })
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Steps Slider
                Text("Sampling Steps: ${steps.toInt()}", color = TextSecondary, fontSize = 12.sp)
                Slider(
                    value = steps,
                    onValueChange = { steps = it },
                    valueRange = 15f..30f,
                    steps = 15,
                    colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan)
                )

                // CFG Slider
                Text("Guidance Scale (CFG): ${"%.1f".format(cfgScale)}", color = TextSecondary, fontSize = 12.sp)
                Slider(
                    value = cfgScale,
                    onValueChange = { cfgScale = it },
                    valueRange = 5.0f..7.5f,
                    colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple)
                )

                // Custom Negative Prompt
                OutlinedTextField(
                    value = customNegative,
                    onValueChange = { customNegative = it },
                    label = { Text("Extra Negative Keywords", color = TextSecondary, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Button: Generate
        Button(
            onClick = {
                if (selectedModel == null) {
                    Toast.makeText(context, "Please select or download a model first!", Toast.LENGTH_SHORT).show()
                    onNavigateToModelHub()
                    return@Button
                }
                isGenerating = true
                GenerationForegroundService.startService(context, "Synthesizing artwork on Galaxy S24...")
                scope.launch {
                    try {
                        engine.loadModel(selectedModel)
                        val config = DiffusionEngine.GenerationConfig(
                            prompt = prompt,
                            negativePrompt = customNegative,
                            width = selectedRatio.width,
                            height = selectedRatio.height,
                            steps = steps.toInt(),
                            cfgScale = cfgScale,
                            enableAnatomyGuard = anatomyGuardEnabled,
                            enableAutoEnhance = autoEnhanceEnabled,
                            enableFaceRefine = faceRefineEnabled
                        )
                        val bmp = engine.generateTxt2Img(config)
                        resultBitmap = bmp
                    } catch (e: Throwable) {
                        Toast.makeText(context, "Notice: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isGenerating = false
                        GenerationForegroundService.stopService(context)
                    }
                }
            },
            enabled = !isGenerating,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentCyan,
                disabledContainerColor = DarkSurfaceVariant
            )
        ) {
            if (isGenerating) {
                CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Generating on Adreno 750...", color = DarkBackground, fontWeight = FontWeight.Bold)
            } else {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = DarkBackground)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Synthesize Artwork", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Live Generation Progress Card
        if (isGenerating) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(progressState.statusMessage, color = TextPrimary, fontSize = 12.sp)
                        if (progressState.totalSteps > 0) {
                            Text(
                                "${(progressState.step * 100) / progressState.totalSteps}%",
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val progressFloat = if (progressState.totalSteps > 0) progressState.step.toFloat() / progressState.totalSteps.toFloat() else 0f
                    LinearProgressIndicator(
                        progress = { progressFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = AccentCyan,
                        trackColor = DarkSurfaceVariant
                    )
                }
            }
        }

        // Result Card
        resultBitmap?.let { bmp ->
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Generated Artwork",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val uri = modelManager.saveImageToGallery(bmp, prompt)
                                    if (uri != null) {
                                        Toast.makeText(context, "Saved to Samsung Gallery (Pictures/AuraDiffusion)!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = DarkBackground)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save to Gallery", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
