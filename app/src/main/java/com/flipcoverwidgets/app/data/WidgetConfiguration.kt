package com.flipcoverwidgets.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "widget_configurations")
data class WidgetConfiguration(
    @PrimaryKey
    val slotNumber: Int,
    val providerPackage: String,
    val providerClass: String,
    val widgetLabel: String,
    val appWidgetId: Int,
    val widgetWidth: Int = 512,
    val widgetHeight: Int = 512,
    val lastUpdated: Long = System.currentTimeMillis()
)
