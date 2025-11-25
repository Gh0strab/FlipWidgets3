package com.flipcoverwidgets.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WidgetConfiguration::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun widgetConfigDao(): WidgetConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE widget_configurations ADD COLUMN widgetWidth INTEGER NOT NULL DEFAULT 512")
                database.execSQL("ALTER TABLE widget_configurations ADD COLUMN widgetHeight INTEGER NOT NULL DEFAULT 512")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE widget_configurations_new (slotNumber INTEGER PRIMARY KEY NOT NULL, providerPackage TEXT NOT NULL, providerClass TEXT NOT NULL, widgetLabel TEXT NOT NULL, appWidgetId INTEGER NOT NULL, widgetWidth INTEGER NOT NULL DEFAULT 512, widgetHeight INTEGER NOT NULL DEFAULT 512, lastUpdated INTEGER NOT NULL)")
                database.execSQL("INSERT INTO widget_configurations_new (slotNumber, providerPackage, providerClass, widgetLabel, appWidgetId, widgetWidth, widgetHeight, lastUpdated) SELECT slotNumber, providerPackage, providerClass, widgetLabel, appWidgetId, widgetWidth, widgetHeight, lastUpdated FROM widget_configurations WHERE id IN (SELECT MAX(id) FROM widget_configurations GROUP BY slotNumber)")
                database.execSQL("DROP TABLE widget_configurations")
                database.execSQL("ALTER TABLE widget_configurations_new RENAME TO widget_configurations")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flipcoverwidgets_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
