package com.flipcoverwidgets.app.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
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
import com.flipcoverwidgets.app.widget.WidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: WidgetRepository
    private var currentSlot: Int = 0
    private var pendingWidgetId: Int = -1
    private var pendingProviderInfo: AppWidgetProviderInfo? = null

    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val appWidgetHost by lazy { 
        com.flipcoverwidgets.app.widget.WidgetHostManager.getHost(this) 
    }

    private val widgetSelectorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data!!
            val providerPackage = data.getStringExtra(WidgetSelectorActivity.RESULT_PROVIDER_PACKAGE)
            val providerClass = data.getStringExtra(WidgetSelectorActivity.RESULT_PROVIDER_CLASS)

            if (providerPackage != null && providerClass != null) {
                val providerComponent = ComponentName(providerPackage, providerClass)
                val providerInfo = appWidgetManager.installedProviders.find { 
                    it.provider == providerComponent 
                }
                
                if (providerInfo != null) {
                    val widgetId = appWidgetHost.allocateAppWidgetId()
                    val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, providerComponent)
                    
                    if (canBind) {
                        proceedWithWidgetConfig(widgetId, providerInfo)
                    } else {
                        pendingWidgetId = widgetId
                        pendingProviderInfo = providerInfo
                        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerComponent)
                        }
                        widgetBindLauncher.launch(bindIntent)
                    }
                }
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
                    val slotNumbers = listOf(1, 2, 3, 4, 6, 7)
                    items(slotNumbers.size) { index ->
                        val slotNumber = slotNumbers[index]
                        val config = configurations.find { it.slotNumber == slotNumber }
                        val sizeLabel = if (slotNumber <= 4) "4x4" else "2x2"
                        WidgetSlotCard(slotNumber, config, sizeLabel)
                    }
                }
            }
        }
    }

    @Composable
    fun WidgetSlotCard(slotNumber: Int, config: WidgetConfiguration?, sizeLabel: String = "") {
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
                        "Slot $slotNumber" + if (sizeLabel.isNotEmpty()) " ($sizeLabel)" else "",
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
        val intent = Intent(this, WidgetSelectorActivity::class.java).apply {
            putExtra(WidgetSelectorActivity.EXTRA_SLOT_NUMBER, slotNumber)
        }
        widgetSelectorLauncher.launch(intent)
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
        try {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        } catch (t: Throwable) {
            Log.w("MainActivity", "Failed to delete appWidgetId $appWidgetId during remove", t)
        }
        repository.removeConfiguration(slotNumber)
        WidgetUpdater.updateAllWidgets(this)
    }
}
