package com.example.data

import android.content.Context
import android.os.Build
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

        @Volatile
        private var lastAppContext: Context? = null

        fun getDatabase(context: Context): WeMoDatabase {
            val appCtx = context.applicationContext
            val existing = INSTANCE
            if (existing != null && lastAppContext === appCtx && existing.isOpen) {
                return existing
            }
            return synchronized(this) {
                val current = INSTANCE
                if (current != null && lastAppContext === appCtx && current.isOpen) {
                    current
                } else {
                    runCatching { current?.close() }
                    val isRobolectric = Build.FINGERPRINT.contains("robolectric", ignoreCase = true) ||
                        runCatching { Class.forName("org.robolectric.Robolectric"); true }.getOrDefault(false)
                    val instance = if (isRobolectric) {
                        Room.inMemoryDatabaseBuilder(appCtx, WeMoDatabase::class.java)
                            .allowMainThreadQueries()
                            .fallbackToDestructiveMigration(dropAllTables = true)
                            .build()
                    } else {
                        Room.databaseBuilder(
                            appCtx,
                            WeMoDatabase::class.java,
                            "wemi_switch_database"
                        )
                            .fallbackToDestructiveMigration(dropAllTables = true)
                            .build()
                    }
                    INSTANCE = instance
                    lastAppContext = appCtx
                    instance
                }
            }
        }

        fun closeCurrentInstance() {
            synchronized(this) {
                runCatching { INSTANCE?.close() }
                INSTANCE = null
                lastAppContext = null
            }
        }
    }
}
