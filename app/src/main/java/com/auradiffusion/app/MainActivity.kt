package com.auradiffusion.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.auradiffusion.app.core.DiffusionEngine
import com.auradiffusion.app.core.ModelManager
import com.auradiffusion.app.core.UpdateManager
import com.auradiffusion.app.ui.components.UpdateDialog
import com.auradiffusion.app.ui.screens.GalleryScreen
import com.auradiffusion.app.ui.screens.Img2ImgScreen
import com.auradiffusion.app.ui.screens.ModelHubScreen
import com.auradiffusion.app.ui.screens.Txt2ImgScreen
import com.auradiffusion.app.ui.theme.AccentCyan
import com.auradiffusion.app.ui.theme.AuraDiffusionTheme
import com.auradiffusion.app.ui.theme.DarkBackground
import com.auradiffusion.app.ui.theme.DarkSurface
import com.auradiffusion.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var engine: DiffusionEngine
    private lateinit var modelManager: ModelManager
    private lateinit var updateManager: UpdateManager
    private var sharedImageUri: Uri? = null

    enum class NavTab(val title: String, val icon: ImageVector) {
        TXT2IMG("Create", Icons.Default.AutoAwesome),
        IMG2IMG("Transform", Icons.Default.AutoFixHigh),
        MODELS("Models", Icons.Default.Memory),
        GALLERY("Gallery", Icons.Default.PhotoLibrary)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        engine = DiffusionEngine.getInstance(this)
        modelManager = ModelManager(this)
        updateManager = UpdateManager(this)

        // Handle shared image from Samsung Gallery
        handleIncomingIntent(intent)

        setContent {
            AuraDiffusionTheme {
                val scope = rememberCoroutineScope()
                var currentTab by remember {
                    mutableStateOf(if (sharedImageUri != null) NavTab.IMG2IMG else NavTab.TXT2IMG)
                }

                val localModels = remember { modelManager.getLocalModels() }
                var selectedModel by remember {
                    mutableStateOf(localModels.firstOrNull())
                }

                val updateState by updateManager.updateState.collectAsState()

                // Check for updates on startup (honors 24h snooze if previously dismissed)
                LaunchedEffect(Unit) {
                    updateManager.checkForUpdates(forceCheck = false)
                }

                // Render in-app Update Popup (Update Now / Update Later - 24h snooze)
                UpdateDialog(
                    updateState = updateState,
                    updateManager = updateManager
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize().background(DarkBackground),
                    bottomBar = {
                        NavigationBar(
                            containerColor = DarkSurface,
                            tonalElevation = 8.dp
                        ) {
                            NavTab.values().forEach { tab ->
                                val isSelected = currentTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            tint = if (isSelected) AccentCyan else TextSecondary
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            color = if (isSelected) AccentCyan else TextSecondary
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = AccentCyan.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = DarkBackground
                    ) {
                        when (currentTab) {
                            NavTab.TXT2IMG -> Txt2ImgScreen(
                                engine = engine,
                                modelManager = modelManager,
                                selectedModel = selectedModel,
                                onNavigateToModelHub = { currentTab = NavTab.MODELS }
                            )
                            NavTab.IMG2IMG -> Img2ImgScreen(
                                engine = engine,
                                modelManager = modelManager,
                                selectedModel = selectedModel,
                                initialImageUri = sharedImageUri,
                                onNavigateToModelHub = { currentTab = NavTab.MODELS }
                            )
                            NavTab.MODELS -> ModelHubScreen(
                                modelManager = modelManager,
                                selectedModel = selectedModel,
                                onSelectModel = { selectedModel = it },
                                onCheckForUpdates = {
                                    scope.launch {
                                        updateManager.checkForUpdates(forceCheck = true)
                                    }
                                }
                            )
                            NavTab.GALLERY -> GalleryScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null) {
                sharedImageUri = uri
            }
        }
    }
}
