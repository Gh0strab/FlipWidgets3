package com.flipcoverwidgets.app.data

import kotlinx.coroutines.flow.Flow

class WidgetRepository(private val dao: WidgetConfigDao) {
    
    fun getAllConfigurations(): Flow<List<WidgetConfiguration>> = dao.getAllConfigurations()
    
    fun getConfigForSlot(slotNumber: Int): Flow<WidgetConfiguration?> = 
        dao.getConfigForSlotFlow(slotNumber)
    
    suspend fun getConfigForSlotSync(slotNumber: Int): WidgetConfiguration? = 
        dao.getConfigForSlot(slotNumber)
    
    suspend fun saveConfiguration(config: WidgetConfiguration) {
        dao.insertConfig(config)
    }
    
    suspend fun removeConfiguration(slotNumber: Int) {
        dao.deleteConfigForSlot(slotNumber)
    }
}
