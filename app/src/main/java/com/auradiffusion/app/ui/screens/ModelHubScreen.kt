package com.auradiffusion.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auradiffusion.app.core.ModelManager
import com.auradiffusion.app.core.NativeSD
import com.auradiffusion.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun ModelHubScreen(
    modelManager: ModelManager,
    selectedModel: File?,
    onSelectModel: (File) -> Unit,
    onCheckForUpdates: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localModels by remember { mutableStateOf(modelManager.getLocalModels()) }
    val downloadState by modelManager.downloadProgress.collectAsState()

    // File picker for importing user's custom GGUF models from device storage
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    Toast.makeText(context, "Importing model...", Toast.LENGTH_SHORT).show()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val dest = File(modelManager.modelsDir, "custom_model_${System.currentTimeMillis()}.gguf")
                            FileOutputStream(dest).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    localModels = modelManager.getLocalModels()
                    localModels.lastOrNull()?.let { onSelectModel(it) }
                    Toast.makeText(context, "Custom model imported successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hardware Profile Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = AccentCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Optimized Hardware Profile", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Target: Samsung Galaxy S24 (Snapdragon 8 Gen 3)",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        "Active Compute: ${NativeSD.activeBackend}",
                        color = AccentEmerald,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        "Memory Budget: Peak 1.6 GB (Safe 8 GB RAM Headroom)",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "App Version: v${com.auradiffusion.app.BuildConfig.VERSION_NAME}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        TextButton(
                            onClick = onCheckForUpdates,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check for Updates", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Curated Mobile Models Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Curated Uncensored Checkpoints", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import .GGUF", color = AccentCyan, fontSize = 11.sp)
                }
            }
        }

        // Curated Models List
        items(ModelManager.CURATED_MODELS) { model ->
            val isDownloaded = localModels.any { it.name == model.filename }
            val isSelected = selectedModel?.name == model.filename

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = if (isSelected) 1.dp else 0.dp,
                        color = if (isSelected) AccentCyan else BorderGlass,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        if (isDownloaded) {
                            localModels.firstOrNull { it.name == model.filename }?.let { onSelectModel(it) }
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(model.description, color = TextSecondary, fontSize = 11.sp)
                        }
                        if (isSelected) {
                            Badge(containerColor = AccentCyan) {
                                Text("ACTIVE", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${model.sizeMb} MB • GGUF Quantized", color = TextSecondary, fontSize = 11.sp)

                        if (!isDownloaded) {
                            val isCurrentlyDownloading = (downloadState as? ModelManager.DownloadState.Downloading)?.modelId == model.id
                            Button(
                                onClick = {
                                    scope.launch {
                                        val ok = modelManager.downloadModel(model)
                                        if (ok) {
                                            localModels = modelManager.getLocalModels()
                                            localModels.firstOrNull { it.name == model.filename }?.let { onSelectModel(it) }
                                            Toast.makeText(context, "${model.name} downloaded & activated!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isCurrentlyDownloading,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                if (isCurrentlyDownloading) {
                                    val percent = (downloadState as ModelManager.DownloadState.Downloading).percent
                                    Text("$percent%", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                } else {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    localModels.firstOrNull { it.name == model.filename }?.let { onSelectModel(it) }
                                }
                            ) {
                                Text(if (isSelected) "Selected" else "Activate", color = AccentEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Progress bar for active download
                    if ((downloadState as? ModelManager.DownloadState.Downloading)?.modelId == model.id) {
                        val state = downloadState as ModelManager.DownloadState.Downloading
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = AccentCyan,
                            trackColor = DarkSurfaceVariant
                        )
                    }
                }
            }
        }

        // Local Models Header
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Installed Models on Device (${localModels.size})", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        // Local Models List
        if (localModels.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "No models installed yet. Tap 'Download' above or import a .GGUF model.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(localModels) { file ->
                val isSelected = selectedModel?.absolutePath == file.absolutePath
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectModel(file) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) AccentCyan.copy(alpha = 0.15f) else DarkSurface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${file.length() / (1024 * 1024)} MB • Local Storage", color = TextSecondary, fontSize = 11.sp)
                        }
                        if (isSelected) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = AccentCyan)
                        }
                    }
                }
            }
        }
    }
}
