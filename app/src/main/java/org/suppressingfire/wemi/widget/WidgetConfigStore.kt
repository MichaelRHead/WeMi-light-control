package org.suppressingfire.wemi.widget

import android.content.Context
import androidx.core.content.edit
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.normalizeRoomName
import org.suppressingfire.wemi.network.LanRuntimeUtils

/**
 * Target mode bound to a placed WeMi Home Screen widget instance.
 */
enum class WidgetTargetType {
    DEVICE,
    ROOM;

    companion object {
        fun fromRaw(raw: String?): WidgetTargetType? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * Persisted configuration for a single Home Screen widget (`appWidgetId`), targeting either
 * an individual smart light/switch or an entire room group.
 */
data class WidgetTargetConfig(
    val appWidgetId: Int,
    val targetType: WidgetTargetType,
    val deviceId: Int = 0,
    val serialNumber: String = "",
    val roomName: String = "",
    val networkKey: String = "",
    val displayName: String = ""
) {
    fun normalizedRoomName(): String = normalizeRoomName(roomName)

    companion object {
        fun forDevice(appWidgetId: Int, device: DeviceEntity): WidgetTargetConfig =
            WidgetTargetConfig(
                appWidgetId = appWidgetId,
                targetType = WidgetTargetType.DEVICE,
                deviceId = device.id,
                serialNumber = device.serialNumber.takeUnless { LanRuntimeUtils.isPlaceholderSerial(it) }.orEmpty(),
                roomName = device.normalizedRoomName,
                networkKey = device.networkKey,
                displayName = device.name.trim().ifBlank { "Smart Light" }
            )

        fun forRoom(appWidgetId: Int, roomName: String, networkKey: String): WidgetTargetConfig {
            val normalized = normalizeRoomName(roomName)
            return WidgetTargetConfig(
                appWidgetId = appWidgetId,
                targetType = WidgetTargetType.ROOM,
                roomName = normalized,
                networkKey = networkKey,
                displayName = normalized
            )
        }
    }
}

/**
 * Lightweight SharedPreferences store mapping each Android `appWidgetId` to its
 * [WidgetTargetConfig]. Keeps widget metadata self-contained without requiring a Room schema bump.
 */
object WidgetConfigStore {
    private const val PREFS_NAME = "wemi_home_widgets"
    private const val KEY_IDS = "configured_widget_ids"

    private fun prefix(appWidgetId: Int): String = "widget_${appWidgetId}_"

    fun save(context: Context, config: WidgetTargetConfig) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingIds = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        existingIds.add(config.appWidgetId.toString())
        val p = prefix(config.appWidgetId)
        prefs.edit {
            putStringSet(KEY_IDS, existingIds)
            putString("${p}type", config.targetType.name)
            putInt("${p}device_id", config.deviceId)
            putString("${p}serial", config.serialNumber)
            putString("${p}room_name", config.roomName)
            putString("${p}network_key", config.networkKey)
            putString("${p}display_name", config.displayName)
        }
    }

    fun load(context: Context, appWidgetId: Int): WidgetTargetConfig? {
        if (appWidgetId <= 0) return null
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val p = prefix(appWidgetId)
        val targetType = WidgetTargetType.fromRaw(prefs.getString("${p}type", null)) ?: return null
        return WidgetTargetConfig(
            appWidgetId = appWidgetId,
            targetType = targetType,
            deviceId = prefs.getInt("${p}device_id", 0),
            serialNumber = prefs.getString("${p}serial", "").orEmpty(),
            roomName = prefs.getString("${p}room_name", "").orEmpty(),
            networkKey = prefs.getString("${p}network_key", "").orEmpty(),
            displayName = prefs.getString("${p}display_name", "").orEmpty()
        )
    }

    fun delete(context: Context, appWidgetId: Int) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingIds = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        existingIds.remove(appWidgetId.toString())
        val p = prefix(appWidgetId)
        prefs.edit {
            putStringSet(KEY_IDS, existingIds)
            remove("${p}type")
            remove("${p}device_id")
            remove("${p}serial")
            remove("${p}room_name")
            remove("${p}network_key")
            remove("${p}display_name")
        }
    }

    fun remapIds(context: Context, oldIds: IntArray, newIds: IntArray) {
        val count = minOf(oldIds.size, newIds.size)
        val remapped = (0 until count).mapNotNull { i ->
            val oldId = oldIds[i]
            val newId = newIds[i]
            if (oldId == newId) return@mapNotNull null
            load(context, oldId)?.let { existing -> oldId to existing.copy(appWidgetId = newId) }
        }
        val destinationIds = remapped.map { it.second.appWidgetId }.toSet()
        remapped.forEach { (oldId, _) ->
            if (oldId !in destinationIds) {
                delete(context, oldId)
            }
        }
        remapped.forEach { (_, updatedConfig) ->
            save(context, updatedConfig)
        }
    }

    fun allConfiguredWidgetIds(context: Context): List<Int> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .filter { it > 0 }
    }

    fun clearAll(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { clear() }
    }
}
