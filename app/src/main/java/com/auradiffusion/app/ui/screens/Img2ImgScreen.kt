package com.auradiffusion.app.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
fun Img2ImgScreen(
    engine: DiffusionEngine,
    modelManager: ModelManager,
    selectedModel: File?,
    initialImageUri: Uri?,
    onNavigateToModelHub: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var sourceUri by remember { mutableStateOf<Uri?>(initialImageUri) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var prompt by remember { mutableStateOf("Hyperrealistic oil painting version, cinematic lighting, 8k uhd, sharp focus") }
    var denoiseStrength by remember { mutableFloatStateOf(0.60f) }
    var anatomyGuardEnabled by remember { mutableStateOf(true) }
    var steps by remember { mutableFloatStateOf(20f) }

    var isGenerating by remember { mutableStateOf(false) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val progressState by engine.progressState.collectAsState()

    // Photo picker launcher (Zero permissions required on modern Android)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            sourceUri = uri
        }
    }

    // Load bitmap when URI changes
    LaunchedEffect(sourceUri) {
        sourceUri?.let { uri ->
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                sourceBitmap = bmp
            } catch (e: Throwable) {
                Toast.makeText(context, "Failed to load selected photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Source Image Selection Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            if (sourceBitmap != null) {
                Box(modifier = Modifier.fillMaxWidth().height(230.dp)) {
                    Image(
                        bitmap = sourceBitmap!!.asImageBitmap(),
                        contentDescription = "Source Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground.copy(alpha = 0.8f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Tap to Change Photo", color = AccentCyan, fontSize = 11.sp)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Select Photo",
                        tint = AccentCyan,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select Photo or Share from Samsung Gallery", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Zero extra storage permissions needed", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Prompt Input
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Transformation Prompt", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
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

        Spacer(modifier = Modifier.height(14.dp))

        // Denoising Strength Slider
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Denoising Strength: ${"%.2f".format(denoiseStrength)}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val label = when {
                        denoiseStrength < 0.35f -> "Subtle Enhancement"
                        denoiseStrength < 0.65f -> "Balanced Stylization"
                        else -> "Creative Re-imagining"
                    }
                    Text(label, color = AccentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = denoiseStrength,
                    onValueChange = { denoiseStrength = it },
                    valueRange = 0.15f..0.90f,
                    colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Anatomy Guard Toggle
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = AccentEmerald)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Anti-Deformation Guard", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Switch(
                    checked = anatomyGuardEnabled,
                    onCheckedChange = { anatomyGuardEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = AccentEmerald)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Transform Button
        Button(
            onClick = {
                val bmp = sourceBitmap
                if (bmp == null) {
                    Toast.makeText(context, "Please select an image first!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (selectedModel == null) {
                    Toast.makeText(context, "Please select or download a model first!", Toast.LENGTH_SHORT).show()
                    onNavigateToModelHub()
                    return@Button
                }

                isGenerating = true
                GenerationForegroundService.startService(context, "Transforming image on Adreno 750...")
                scope.launch {
                    try {
                        engine.loadModel(selectedModel)
                        val config = DiffusionEngine.GenerationConfig(
                            prompt = prompt,
                            width = 512,
                            height = 512,
                            steps = steps.toInt(),
                            cfgScale = 6.5f,
                            denoiseStrength = denoiseStrength,
                            enableAnatomyGuard = anatomyGuardEnabled
                        )
                        val out = engine.generateImg2Img(bmp, config)
                        resultBitmap = out
                    } catch (e: Throwable) {
                        Toast.makeText(context, "Notice: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isGenerating = false
                        GenerationForegroundService.stopService(context)
                    }
                }
            },
            enabled = !isGenerating && sourceBitmap != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Transforming on Adreno GPU...", color = DarkBackground, fontWeight = FontWeight.Bold)
            } else {
                Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, tint = DarkBackground)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Transform Image", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Result Card
        resultBitmap?.let { outBmp ->
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Transformation Result", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Image(
                        bitmap = outBmp.asImageBitmap(),
                        contentDescription = "Result",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val uri = modelManager.saveImageToGallery(outBmp, prompt)
                                if (uri != null) {
                                    Toast.makeText(context, "Saved to Samsung Gallery (Pictures/AuraDiffusion)!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
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
