package org.suppressingfire.wemi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.suppressingfire.wemi.network.LanRuntimeUtils

@Database(
    entities = [
        DeviceEntity::class,
        RuleEntity::class,
        HomeNetworkEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class SwitchDatabase : RoomDatabase() {
    abstract fun switchDao(): SwitchDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE wemo_devices ADD COLUMN hardwareRulesCapability TEXT NOT NULL DEFAULT 'UNKNOWN'"
                )
                db.execSQL(
                    "ALTER TABLE wemo_devices ADD COLUMN hardwareRulesLongPressCapable INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE wemo_devices ADD COLUMN hardwareRulesVerifiedFirmware TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        @Volatile
        private var INSTANCE: SwitchDatabase? = null

        @Volatile
        private var lastAppContext: Context? = null

        fun getDatabase(context: Context): SwitchDatabase {
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
                    val isRobolectric = LanRuntimeUtils.isRunningInRobolectric()
                    val instance = if (isRobolectric) {
                        Room.inMemoryDatabaseBuilder(appCtx, SwitchDatabase::class.java)
                            .allowMainThreadQueries()
                            .addMigrations(MIGRATION_5_6)
                            .fallbackToDestructiveMigration(dropAllTables = true)
                            .build()
                    } else {
                        Room.databaseBuilder(
                            appCtx,
                            SwitchDatabase::class.java,
                            "wemi_switch_database"
                        )
                            .addMigrations(MIGRATION_5_6)
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
