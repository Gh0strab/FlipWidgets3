package com.flipcoverwidgets.app.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.flipcoverwidgets.app.FlipCoverWidgetsApp
import com.flipcoverwidgets.app.data.WidgetConfiguration
import com.flipcoverwidgets.app.data.WidgetRepository
import com.flipcoverwidgets.app.service.WidgetMirrorService
import com.flipcoverwidgets.app.widget.WidgetHostManager
import com.flipcoverwidgets.app.widget.WidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: WidgetRepository
    private var currentSlot: Int = 0
    private var pendingWidgetId: Int = -1
    private var pendingProviderInfo: AppWidgetProviderInfo? = null
    private var currentPickerWidgetId: Int = -1

    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val appWidgetHost by lazy { WidgetHostManager.getHost(this) }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w("MainActivity", "Overlay permission not granted")
        }
    }

    private val widgetPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Robust handling: extract extras from returned intent (picker will include them)
        if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data!!
            val pickedId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            val providerComponent = data.getParcelableExtra<ComponentName>(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)

            // Clear the temp allocated id if we didn't pick it
            if (pickedId == -1) {
                if (currentPickerWidgetId != -1) {
                    // Delete what we allocated earlier just in case
                    appWidgetHost.deleteAppWidgetId(currentPickerWidgetId)
                    currentPickerWidgetId = -1
                }
                return@registerForActivityResult
            }

            // If the system returned the provider component, try to use it directly
            if (providerComponent != null) {
                val providerInfo = appWidgetManager.getAppWidgetInfo(pickedId)
                if (providerInfo != null) {
                    // Normal path
                    proceedWithWidgetConfig(pickedId, providerInfo)
                    currentPickerWidgetId = -1
                    return@registerForActivityResult
                }
            }

            // Otherwise, sometimes the provider mapping isn't available instantly.
            // Try a short retry loop before giving up — this covers the "user hasn't tapped accept immediately" timing case.
            lifecycleScope.launch {
                var providerInfo: AppWidgetProviderInfo? = null
                val maxAttempts = 6
                repeat(maxAttempts) { attempt ->
                    providerInfo = appWidgetManager.getAppWidgetInfo(pickedId)
                    if (providerInfo != null) return@repeat
                    delay(200L)
                }

                if (providerInfo != null) {
                    proceedWithWidgetConfig(pickedId, providerInfo!!)
                } else {
                    // Still not available — safe cleanup.
                    try {
                        appWidgetHost.deleteAppWidgetId(pickedId)
                    } catch (t: Throwable) {
                        Log.w("MainActivity", "Failed to delete appWidgetId $pickedId", t)
                    }
                }

                currentPickerWidgetId = -1
            }

        } else {
            // User cancelled; clean up any allocated id
            if (currentPickerWidgetId != -1) {
                try {
                    appWidgetHost.deleteAppWidgetId(currentPickerWidgetId)
                } catch (t: Throwable) {
                    Log.w("MainActivity", "Failed to delete appWidgetId $currentPickerWidgetId on cancel", t)
                }
                currentPickerWidgetId = -1
            }
        }
    }

    private val widgetBindLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && pendingWidgetId != -1 && pendingProviderInfo != null) {
            proceedWithWidgetConfig(pendingWidgetId, pendingProviderInfo!!)
        } else {
            if (pendingWidgetId != -1) {
                try {
                    appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                } catch (t: Throwable) {
                    Log.w("MainActivity", "Failed to delete pendingWidgetId $pendingWidgetId", t)
                }
            }
            pendingWidgetId = -1
            pendingProviderInfo = null
        }
    }

    private val widgetConfigLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val widgetId = pendingWidgetId
        val providerInfo = pendingProviderInfo
        val slot = currentSlot

        pendingWidgetId = -1
        pendingProviderInfo = null

        if (result.resultCode == RESULT_OK && widgetId != -1 && providerInfo != null) {
            lifecycleScope.launch {
                saveWidgetConfigAndCapture(slot, widgetId, providerInfo)
            }
        } else {
            if (widgetId != -1) {
                try {
                    appWidgetHost.deleteAppWidgetId(widgetId)
                } catch (t: Throwable) {
                    Log.w("MainActivity", "Failed to delete widgetId $widgetId after config cancel/fail", t)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = WidgetRepository(
            (application as FlipCoverWidgetsApp).database.widgetConfigDao()
        )

        WidgetHostManager.startListening(this)

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }

        setContent {
            FlipCoverWidgetsTheme {
                MainScreen()
            }
        }
    }

    @Composable
    fun FlipCoverWidgetsTheme(content: @Composable () -> Unit) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF6200EE),
                secondary = Color(0xFF03DAC5),
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E)
            ),
            content = content
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val configurations by repository.getAllConfigurations()
            .collectAsState(initial = emptyList())

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("FlipCoverWidgets") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    "Configure your cover screen widgets",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    "Each slot will appear in Samsung Settings > Cover screen > Widgets as 'FlipCoverWidget Slot X'",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(4) { index ->
                        val slotNumber = index + 1
                        val config = configurations.find { it.slotNumber == slotNumber }
                        WidgetSlotCard(slotNumber, config)
                    }
                }
            }
        }
    }

    @Composable
    fun WidgetSlotCard(slotNumber: Int, config: WidgetConfiguration?) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectWidgetForSlot(slotNumber) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Slot $slotNumber",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (config != null) {
                        Text(
                            config.widgetLabel,
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(
                            "Tap to select widget",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (config != null) {
                    Button(
                        onClick = {
                            lifecycleScope.launch {
                                removeWidget(slotNumber, config.appWidgetId)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red
                        )
                    ) {
                        Text("Remove")
                    }
                }
            }
        }
    }

    private fun selectWidgetForSlot(slotNumber: Int) {
        currentSlot = slotNumber

        currentPickerWidgetId = appWidgetHost.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, currentPickerWidgetId)
        }

        widgetPickerLauncher.launch(pickIntent)
    }

    private fun handleWidgetSelection() {
        // kept for backward compatibility but not used in the updated picker flow
        // selection handling is now done in widgetPickerLauncher where extras are read
    }

    private fun proceedWithWidgetConfig(appWidgetId: Int, providerInfo: AppWidgetProviderInfo) {
        pendingWidgetId = appWidgetId
        pendingProviderInfo = providerInfo

        if (providerInfo.configure != null) {
            Log.d("MainActivity", "Widget requires configuration. Launching config activity for widgetId: $appWidgetId")
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = providerInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            widgetConfigLauncher.launch(configIntent)
        } else {
            Log.d("MainActivity", "Widget does not require configuration. Proceeding directly for widgetId: $appWidgetId")
            val slot = currentSlot
            pendingWidgetId = -1
            pendingProviderInfo = null
            lifecycleScope.launch {
                saveWidgetConfigAndCapture(slot, appWidgetId, providerInfo)
            }
        }
    }

    private suspend fun saveWidgetConfigAndCapture(
        slotNumber: Int,
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo
    ) {
        repository.getConfigForSlotSync(slotNumber)?.let { oldConfig ->
            WidgetMirrorService.unbindWidget(this, oldConfig.appWidgetId)
            try {
                appWidgetHost.deleteAppWidgetId(oldConfig.appWidgetId)
            } catch (t: Throwable) {
                Log.w("MainActivity", "Failed to delete old appWidgetId ${oldConfig.appWidgetId}", t)
            }
        }

        val config = WidgetConfiguration(
            slotNumber = slotNumber,
            providerPackage = providerInfo.provider.packageName,
            providerClass = providerInfo.provider.className,
            widgetLabel = providerInfo.loadLabel(packageManager),
            appWidgetId = appWidgetId,
            widgetWidth = providerInfo.minWidth,
            widgetHeight = providerInfo.minHeight
        )

        repository.saveConfiguration(config)

        WidgetMirrorService.bindWidget(this, appWidgetId, providerInfo, providerInfo.minWidth, providerInfo.minHeight)

        captureAndSaveWidget(slotNumber)

        WidgetUpdater.updateAllWidgets(this)
    }

    private suspend fun captureAndSaveWidget(slotNumber: Int) {
        val config = repository.getConfigForSlotSync(slotNumber) ?: return

        kotlinx.coroutines.delay(500)

        WidgetMirrorService.captureWidgetSnapshot(this, config.appWidgetId)

        WidgetUpdater.updateAllWidgets(this)
    }

    private suspend fun removeWidget(slotNumber: Int, appWidgetId: Int) {
        WidgetMirrorService.unbindWidget(this, appWidgetId)
        try {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        } catch (t: Throwable) {
            Log.w("MainActivity", "Failed to delete appWidgetId $appWidgetId during remove", t)
        }
        repository.removeConfiguration(slotNumber)
        WidgetUpdater.updateAllWidgets(this)
    }
}
