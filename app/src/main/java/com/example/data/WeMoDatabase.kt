package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WeMoDeviceEntity::class,
        WeMoRuleEntity::class,
        WeMoHomeNetworkEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class WeMoDatabase : RoomDatabase() {
    abstract fun wemoDao(): WeMoDao

    companion object {
        @Volatile
        private var INSTANCE: WeMoDatabase? = null

        fun getDatabase(context: Context): WeMoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WeMoDatabase::class.java,
                    "wemi_switch_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
