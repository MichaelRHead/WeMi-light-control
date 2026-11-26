package org.suppressingfire.wemi.network

import android.database.sqlite.SQLiteDatabase
import java.util.Locale

/**
 * Security hardening and structural validation guard for untrusted SQLite `rules.db` files
 * downloaded over the local LAN (or restored from raw-ZIP backups) before any table query,
 * `PRAGMA integrity_check`, or `INSERT`/`UPDATE`/`DELETE` executes.
 *
 * Defends against malicious LAN peers injecting SQLite triggers, views, virtual tables,
 * unsafe schema expressions, or row-flood DoS payloads into Android's native `libsqlite`.
 */
internal object HardwareRulesSchemaGuard {

    const val MAX_ZIP_ENTRIES = 32
    const val MAX_RULES_ROWS = 500
    const val MAX_RULE_DEVICES_ROWS = 5_000
    const val MAX_TARGET_DEVICES_ROWS = 5_000
    const val MAX_DEVICE_COMBINATION_ROWS = 5_000

    private val SAFE_IDENTIFIER_REGEX = Regex("^[A-Za-z0-9_]{1,64}$")
    private val VIRTUAL_TABLE_REGEX = Regex("\\bCREATE\\s+VIRTUAL\\s+TABLE\\b", RegexOption.IGNORE_CASE)
    private val BELKIN_CORE_TABLES = setOf(
        "RULES",
        "RULEDEVICES",
        "TARGETDEVICES",
        "DEVICECOMBINATION",
        "LOCATIONINFO",
        "GROUPDEVICES",
        "BLOCKEDRULES",
        "RULESNOTIFYMESSAGE",
        "SENSORNOTIFICATION"
    )

    /**
     * Applies defensive SQLite PRAGMAs (`trusted_schema = OFF`, and `query_only = ON` when
     * [readOnly] is `true`) and inspects `sqlite_master` BEFORE any application query or write.
     *
     * Returns a list of security violations (empty when safe). Throws [SecurityException] when
     * [throwOnViolation] is `true` and any violation is detected.
     */
    fun hardenAndValidateSchema(
        db: SQLiteDatabase,
        readOnly: Boolean,
        throwOnViolation: Boolean = false
    ): List<String> {
        // Disable untrusted schema features (triggers/views/expressions using dangerous functions)
        runCatching { db.execSQL("PRAGMA trusted_schema = OFF") }
        runCatching { db.execSQL("PRAGMA cell_size_check = ON") }
        if (readOnly) {
            runCatching { db.execSQL("PRAGMA query_only = ON") }
        }

        val issues = mutableListOf<String>()
        db.rawQuery("SELECT type, name, sql FROM sqlite_master", null).use { c ->
            var objectCount = 0
            while (c.moveToNext()) {
                objectCount++
                if (objectCount > 256) {
                    issues += "SQLite schema exceeds safe object count limit (256)"
                    break
                }
                val objType = HardwareRulesDbCodec.cursorString(c, 0).lowercase(Locale.US)
                val objName = HardwareRulesDbCodec.cursorString(c, 1)
                val objSql = HardwareRulesDbCodec.cursorString(c, 2)
                when (objType) {
                    "trigger" -> issues += "Rejected untrusted SQLite trigger '$objName' in rules.db"
                    "view" -> issues += "Rejected untrusted SQLite view '$objName' in rules.db"
                    "table" -> {
                        if (VIRTUAL_TABLE_REGEX.containsMatchIn(objSql)) {
                            issues += "Rejected untrusted SQLite virtual table '$objName' in rules.db"
                        }
                    }
                }
                val upperName = objName.uppercase(Locale.US)
                if (upperName in BELKIN_CORE_TABLES && objType != "table") {
                    issues += "Belkin core object '$objName' must be a table (found '$objType')"
                }
            }
        }

        if (throwOnViolation && issues.isNotEmpty()) {
            throw SecurityException(issues.joinToString("; "))
        }
        return issues
    }

    /**
     * Validates a SQLite table identifier strictly against `[A-Za-z0-9_]{1,64}` before interpolating
     * into `PRAGMA table_info(...)`.
     */
    fun validateTableIdentifier(tableName: String): String {
        val clean = tableName.trim()
        require(SAFE_IDENTIFIER_REGEX.matches(clean)) {
            "Unsafe SQLite table identifier: $tableName"
        }
        return clean
    }
}
