package com.auradiffusion.app.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
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
import androidx.core.content.FileProvider
import com.auradiffusion.app.ui.theme.*
import java.io.File

@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    var selectedFile by remember { mutableStateOf<File?>(null) }

    val galleryDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "AuraDiffusion"
    )

    var imageFiles by remember {
        mutableStateOf(
            if (galleryDir.exists()) {
                galleryDir.listFiles()?.filter { it.extension.lowercase() in listOf("png", "jpg", "jpeg") }
                    ?.sortedByDescending { it.lastModified() } ?: emptyList()
            } else {
                emptyList()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Creations Gallery", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${imageFiles.size} images saved in Pictures/AuraDiffusion", color = TextSecondary, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (imageFiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No creations yet", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Generated images will appear here and in Samsung Gallery", color = TextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(imageFiles) { file ->
                    val bitmap = remember(file.absolutePath) {
                        try {
                            BitmapFactory.decodeFile(file.absolutePath)
                        } catch (_: Throwable) {
                            null
                        }
                    }

                    if (bitmap != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedFile = file },
                            colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = file.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Preview Dialog
    selectedFile?.let { file ->
        val fullBmp = remember(file.absolutePath) {
            BitmapFactory.decodeFile(file.absolutePath)
        }

        AlertDialog(
            onDismissRequest = { selectedFile = null },
            confirmButton = {
                TextButton(onClick = { selectedFile = null }) {
                    Text("Close", color = AccentCyan)
                }
            },
            dismissButton = {
                Row {
                    IconButton(
                        onClick = {
                            try {
                                val uri = Uri.fromFile(file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share AI Creation"))
                            } catch (_: Throwable) {}
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = AccentCyan)
                    }
                    IconButton(
                        onClick = {
                            file.delete()
                            imageFiles = galleryDir.listFiles()?.filter { it.extension in listOf("png", "jpg") } ?: emptyList()
                            selectedFile = null
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                    }
                }
            },
            title = { Text(file.name, color = TextPrimary, fontSize = 14.sp) },
            text = {
                if (fullBmp != null) {
                    Image(
                        bitmap = fullBmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            containerColor = DarkSurface
        )
    }
}
