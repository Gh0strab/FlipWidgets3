package com.flipcoverwidgets.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetConfigDao {
    
    @Query("SELECT * FROM widget_configurations WHERE slotNumber = :slotNumber LIMIT 1")
    suspend fun getConfigForSlot(slotNumber: Int): WidgetConfiguration?
    
    @Query("SELECT * FROM widget_configurations WHERE appWidgetId = :appWidgetId LIMIT 1")
    suspend fun getConfigByWidgetId(appWidgetId: Int): WidgetConfiguration?
    
    @Query("SELECT * FROM widget_configurations WHERE slotNumber = :slotNumber LIMIT 1")
    fun getConfigForSlotFlow(slotNumber: Int): Flow<WidgetConfiguration?>
    
    @Query("SELECT * FROM widget_configurations ORDER BY slotNumber")
    fun getAllConfigurations(): Flow<List<WidgetConfiguration>>
    
    @Query("SELECT * FROM widget_configurations ORDER BY slotNumber")
    suspend fun getAllConfigs(): List<WidgetConfiguration>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: WidgetConfiguration)
    
    @Query("DELETE FROM widget_configurations WHERE slotNumber = :slotNumber")
    suspend fun deleteConfigForSlot(slotNumber: Int)
    
    @Query("DELETE FROM widget_configurations")
    suspend fun deleteAll()
}
