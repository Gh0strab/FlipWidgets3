package com.flipcoverwidgets.app.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetSelectorActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_SLOT_NUMBER = "slot_number"
        const val RESULT_PROVIDER_PACKAGE = "provider_package"
        const val RESULT_PROVIDER_CLASS = "provider_class"
    }

    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var slotNumber: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        slotNumber = intent.getIntExtra(EXTRA_SLOT_NUMBER, 0)
        
        setContent {
            WidgetSelectorTheme {
                WidgetSelectorScreen()
            }
        }
    }

    @Composable
    fun WidgetSelectorTheme(content: @Composable () -> Unit) {
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

    data class AppWidgetGroup(
        val packageName: String,
        val appName: String,
        val appIcon: Drawable?,
        val widgets: List<AppWidgetProviderInfo>
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun WidgetSelectorScreen() {
        val context = LocalContext.current
        var isLoading by remember { mutableStateOf(true) }
        val widgetGroups = remember { mutableStateListOf<AppWidgetGroup>() }
        val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val providers = appWidgetManager.installedProviders
                val pm = context.packageManager

                val grouped = providers.groupBy { it.provider.packageName }
                    .map { (packageName, widgets) ->
                        val appInfo = try {
                            pm.getApplicationInfo(packageName, 0)
                        } catch (e: Exception) {
                            null
                        }
                        val appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName
                        val appIcon = appInfo?.let { pm.getApplicationIcon(it) }
                        
                        AppWidgetGroup(
                            packageName = packageName,
                            appName = appName,
                            appIcon = appIcon,
                            widgets = widgets.sortedBy { it.loadLabel(pm) }
                        )
                    }
                    .sortedBy { it.appName.lowercase() }

                withContext(Dispatchers.Main) {
                    widgetGroups.clear()
                    widgetGroups.addAll(grouped)
                    isLoading = false
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Select Widget for Slot $slotNumber") },
                    navigationIcon = {
                        IconButton(onClick = { 
                            setResult(RESULT_CANCELED)
                            finish() 
                        }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading widgets...", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 8.dp)
                ) {
                    items(widgetGroups) { group ->
                        val isExpanded = expandedGroups[group.packageName] ?: false
                        
                        AppGroupHeader(
                            group = group,
                            isExpanded = isExpanded,
                            onToggle = { expandedGroups[group.packageName] = !isExpanded }
                        )
                        
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, bottom = 8.dp)
                            ) {
                                group.widgets.forEach { widget ->
                                    WidgetItem(
                                        widget = widget,
                                        onSelect = { selectWidget(widget) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun AppGroupHeader(
        group: AppWidgetGroup,
        isExpanded: Boolean,
        onToggle: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onToggle() },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isExpanded) 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                else 
                    MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                group.appIcon?.let { icon ->
                    Image(
                        bitmap = icon.toBitmap(48, 48).asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        text = "${group.widgets.size} widget${if (group.widgets.size != 1) "s" else ""}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                Icon(
                    imageVector = if (isExpanded) 
                        Icons.Default.KeyboardArrowUp 
                    else 
                        Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color.White
                )
            }
        }
    }

    @Composable
    fun WidgetItem(
        widget: AppWidgetProviderInfo,
        onSelect: () -> Unit
    ) {
        val context = LocalContext.current
        val pm = context.packageManager
        val widgetLabel = widget.loadLabel(pm)
        val widgetIcon = widget.loadIcon(context, resources.displayMetrics.densityDpi)
        val sizeText = "${widget.minWidth}dp x ${widget.minHeight}dp"
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onSelect() },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2A2A))
                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val previewDrawable = if (widget.previewImage != 0) {
                        try {
                            pm.getResourcesForApplication(widget.provider.packageName)
                                .getDrawable(widget.previewImage, null)
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                    
                    if (previewDrawable != null) {
                        Image(
                            bitmap = previewDrawable.toBitmap(160, 160).asImageBitmap(),
                            contentDescription = widgetLabel,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        WidgetIconFallback(widgetIcon, widgetLabel)
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = widgetLabel,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = sizeText,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    val description = widget.loadDescription(context)
                    if (description != null) {
                        Text(
                            text = description.toString(),
                            fontSize = 11.sp,
                            color = Color.Gray.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun WidgetIconFallback(icon: Drawable?, label: String) {
        if (icon != null) {
            Image(
                bitmap = icon.toBitmap(64, 64).asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.size(48.dp)
            )
        } else {
            Text(
                text = label.take(2).uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
        }
    }

    private fun selectWidget(providerInfo: AppWidgetProviderInfo) {
        val resultIntent = Intent().apply {
            putExtra(RESULT_PROVIDER_PACKAGE, providerInfo.provider.packageName)
            putExtra(RESULT_PROVIDER_CLASS, providerInfo.provider.className)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
