package org.suppressingfire.wemi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import org.suppressingfire.wemi.R
import org.suppressingfire.wemi.data.DeviceCategory
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.network.LanRuntimeUtils

enum class WidgetVisualMode {
    ON,
    OFF,
    OFFLINE,
    UNCONFIGURED
}

data class WidgetDisplayState(
    val title: String,
    val subtitle: String,
    val visualMode: WidgetVisualMode,
    val iconResId: Int,
    val contentDescription: String
)

/**
 * Pure renderer & updater for WeMi Home Screen light and room widgets.
 */
object WidgetRemoteViewsBuilder {
    private const val COLOR_TEXT_TITLE = 0xFF0F172A.toInt()
    private const val COLOR_TEXT_ACTIVE = 0xFF0E7490.toInt()
    private const val COLOR_TEXT_STANDBY = 0xFF64748B.toInt()
    private const val COLOR_TEXT_OFFLINE = 0xFFDC2626.toInt()

    private fun hasConflictingHardwareIdentity(candidate: DeviceEntity, config: WidgetTargetConfig): Boolean {
        val serialConflict = !LanRuntimeUtils.isPlaceholderSerial(config.serialNumber) &&
            !LanRuntimeUtils.isPlaceholderSerial(candidate.serialNumber) &&
            !candidate.serialNumber.equals(config.serialNumber, ignoreCase = true)
        if (serialConflict) return true
        val configUdn = LanRuntimeUtils.canonicalHardwareUdn(config.udn)
        val candidateUdn = LanRuntimeUtils.canonicalHardwareUdn(candidate.udn)
        return configUdn.isNotEmpty() && candidateUdn.isNotEmpty() &&
            !candidateUdn.equals(configUdn, ignoreCase = true)
    }

    /**
     * Resolves a configured [WidgetTargetConfig] targeting a single device against the latest
     * Room device list. Prioritizes Tier-1 real [DeviceEntity.serialNumber] and Tier-2 canonical
     * [DeviceEntity.udn] so recycled SQLite row IDs after a LAN reset never hijack a different
     * switch, then falls back to `deviceId` or `(displayName, networkKey)` while rejecting any
     * candidate with a conflicting real serial or canonical UDN.
     */
    fun resolveTargetDevice(
        config: WidgetTargetConfig,
        devices: List<DeviceEntity>
    ): DeviceEntity? {
        if (config.targetType != WidgetTargetType.DEVICE) return null

        if (!LanRuntimeUtils.isPlaceholderSerial(config.serialNumber)) {
            val bySerial = devices.firstOrNull {
                !LanRuntimeUtils.isPlaceholderSerial(it.serialNumber) &&
                    it.serialNumber.equals(config.serialNumber, ignoreCase = true)
            }
            if (bySerial != null) return bySerial
        }

        val configUdn = LanRuntimeUtils.canonicalHardwareUdn(config.udn)
        if (configUdn.isNotEmpty()) {
            val byUdn = devices.firstOrNull {
                LanRuntimeUtils.canonicalHardwareUdn(it.udn).equals(configUdn, ignoreCase = true) &&
                    !hasConflictingHardwareIdentity(it, config)
            }
            if (byUdn != null) return byUdn
        }

        if (config.deviceId > 0) {
            val byId = devices.firstOrNull {
                it.id == config.deviceId && !hasConflictingHardwareIdentity(it, config)
            }
            if (byId != null) return byId
        }

        if (config.displayName.isNotBlank()) {
            val byNameInHome = devices.firstOrNull {
                it.name.equals(config.displayName, ignoreCase = true) &&
                    (config.networkKey.isBlank() || it.networkKey == config.networkKey) &&
                    !hasConflictingHardwareIdentity(it, config)
            }
            if (byNameInHome != null) return byNameInHome
            return devices.firstOrNull {
                it.name.equals(config.displayName, ignoreCase = true) &&
                    !hasConflictingHardwareIdentity(it, config)
            }
        }
        return null
    }

    /**
     * Resolves all controllable lights/switches belonging to a configured Room widget.
     * Keeps widgets isolated to their configured Wi-Fi Home while that home still exists in Room,
     * and recovers cleanly to a single matching home if the old `networkKey` was replaced by a LAN reset.
     */
    fun resolveRoomDevices(
        config: WidgetTargetConfig,
        devices: List<DeviceEntity>
    ): List<DeviceEntity> {
        if (config.targetType != WidgetTargetType.ROOM) return emptyList()
        val normalizedRoom = config.normalizedRoomName()
        val controllable = devices.filter { it.parsedType != DeviceType.MOTION_SENSOR }
        if (config.networkKey.isNotBlank()) {
            val inConfiguredHome = controllable.filter {
                it.networkKey == config.networkKey &&
                    it.normalizedRoomName.equals(normalizedRoom, ignoreCase = true)
            }
            if (inConfiguredHome.isNotEmpty()) return inConfiguredHome
            if (devices.any { it.networkKey == config.networkKey }) return emptyList()
        }

        val fallbackMatches = controllable.filter {
            it.normalizedRoomName.equals(normalizedRoom, ignoreCase = true)
        }
        val fallbackHomeKey = fallbackMatches.firstOrNull()?.networkKey ?: return emptyList()
        return fallbackMatches.filter { it.networkKey == fallbackHomeKey }
    }

    fun resolveDisplayState(
        config: WidgetTargetConfig?,
        devices: List<DeviceEntity>,
        isUpdating: Boolean = false
    ): WidgetDisplayState {
        if (config == null) {
            return WidgetDisplayState(
                title = "WeMi Light Widget",
                subtitle = "Tap to choose light or room",
                visualMode = WidgetVisualMode.UNCONFIGURED,
                iconResId = R.drawable.ic_widget_light,
                contentDescription = "Configure WeMi light or room widget"
            )
        }

        return when (config.targetType) {
            WidgetTargetType.DEVICE -> {
                val device = resolveTargetDevice(config, devices)
                if (device == null) {
                    val label = config.displayName.ifBlank { "Removed Switch" }
                    WidgetDisplayState(
                        title = label,
                        subtitle = if (isUpdating) "Updating..." else "Tap to reconfigure",
                        visualMode = WidgetVisualMode.OFFLINE,
                        iconResId = R.drawable.ic_widget_switch,
                        contentDescription = "$label unavailable, tap to reconfigure"
                    )
                } else {
                    val iconRes = when {
                        device.parsedType == DeviceType.LIGHTBULB_DIMMER ||
                            device.visualCategory == DeviceCategory.LIGHT -> R.drawable.ic_widget_light
                        else -> R.drawable.ic_widget_switch
                    }
                    val mode = when {
                        !device.isReachable -> WidgetVisualMode.OFFLINE
                        device.isOn -> WidgetVisualMode.ON
                        else -> WidgetVisualMode.OFF
                    }
                    val subtitle = when {
                        isUpdating -> "Updating..."
                        !device.isReachable -> "Offline • Tap to retry"
                        device.parsedType == DeviceType.LIGHTBULB_DIMMER && device.isOn ->
                            "ON • ${device.brightness}%"
                        device.isOn -> "ON • ${device.normalizedRoomName}"
                        else -> "OFF • ${device.normalizedRoomName}"
                    }
                    val stateWord = when (mode) {
                        WidgetVisualMode.ON -> "on"
                        WidgetVisualMode.OFF -> "off"
                        WidgetVisualMode.OFFLINE -> "offline"
                        WidgetVisualMode.UNCONFIGURED -> "not configured"
                    }
                    WidgetDisplayState(
                        title = device.name,
                        subtitle = subtitle,
                        visualMode = mode,
                        iconResId = iconRes,
                        contentDescription = "${device.name}, $stateWord. Tap to toggle power."
                    )
                }
            }

            WidgetTargetType.ROOM -> {
                val roomName = config.normalizedRoomName()
                val roomSwitches = resolveRoomDevices(config, devices)
                val reachable = roomSwitches.filter { it.isReachable }
                val activeCount = reachable.count { it.isOn }
                val mode = when {
                    roomSwitches.isEmpty() -> WidgetVisualMode.OFF
                    reachable.isEmpty() -> WidgetVisualMode.OFFLINE
                    activeCount > 0 -> WidgetVisualMode.ON
                    else -> WidgetVisualMode.OFF
                }
                val subtitle = when {
                    isUpdating -> "Updating room..."
                    roomSwitches.isEmpty() -> "No lights in room"
                    reachable.isEmpty() -> "Offline • Room"
                    activeCount > 0 -> "$activeCount/${reachable.size} ON • Room"
                    else -> "All OFF • ${reachable.size} ${if (reachable.size == 1) "light" else "lights"}"
                }
                val actionDesc = if (activeCount > 0) "Tap to turn room off." else "Tap to turn room on."
                WidgetDisplayState(
                    title = roomName,
                    subtitle = subtitle,
                    visualMode = mode,
                    iconResId = R.drawable.ic_widget_room,
                    contentDescription = "$roomName room, $subtitle. $actionDesc"
                )
            }
        }
    }

    fun buildRemoteViews(
        context: Context,
        appWidgetId: Int,
        config: WidgetTargetConfig?,
        devices: List<DeviceEntity>,
        isUpdating: Boolean = false
    ): RemoteViews {
        val state = resolveDisplayState(config, devices, isUpdating)
        val views = applyDisplayState(context, state)

        val tapIntent = if (config == null ||
            (config.targetType == WidgetTargetType.DEVICE && resolveTargetDevice(config, devices) == null)
        ) {
            val cfgIntent = Intent(context, SwitchWidgetConfigureActivity::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            PendingIntent.getActivity(
                context,
                appWidgetId + 100_000,
                cfgIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            val toggleIntent = Intent(context, WeMiSwitchWidgetProvider::class.java).apply {
                action = WeMiSwitchWidgetProvider.ACTION_TOGGLE_WIDGET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        views.setOnClickPendingIntent(R.id.widget_root, tapIntent)
        views.setOnClickPendingIntent(R.id.widget_power_ring, tapIntent)
        return views
    }

    fun buildPreviewRemoteViews(
        context: Context,
        config: WidgetTargetConfig,
        devices: List<DeviceEntity>
    ): RemoteViews = applyDisplayState(context, resolveDisplayState(config, devices, isUpdating = false))

    private fun applyDisplayState(context: Context, state: WidgetDisplayState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_switch_control)
        val (bgRes, badgeRes, powerRes, subtitleColor) = when (state.visualMode) {
            WidgetVisualMode.ON -> Quad(
                R.drawable.widget_bg_on,
                R.drawable.widget_icon_badge_on,
                R.drawable.ic_widget_power_on,
                COLOR_TEXT_ACTIVE
            )
            WidgetVisualMode.OFFLINE -> Quad(
                R.drawable.widget_bg_offline,
                R.drawable.widget_icon_badge_offline,
                R.drawable.ic_widget_power_offline,
                COLOR_TEXT_OFFLINE
            )
            WidgetVisualMode.OFF, WidgetVisualMode.UNCONFIGURED -> Quad(
                R.drawable.widget_bg_off,
                R.drawable.widget_icon_badge_off,
                R.drawable.ic_widget_power_off,
                COLOR_TEXT_STANDBY
            )
        }

        views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
        views.setInt(R.id.widget_icon_badge, "setBackgroundResource", badgeRes)
        views.setImageViewResource(R.id.widget_type_icon, state.iconResId)
        views.setTextViewText(R.id.widget_title, state.title)
        views.setTextViewText(R.id.widget_subtitle, state.subtitle)
        views.setTextColor(R.id.widget_subtitle, subtitleColor)
        views.setTextColor(R.id.widget_title, COLOR_TEXT_TITLE)
        views.setImageViewResource(R.id.widget_power_ring, powerRes)
        views.setContentDescription(R.id.widget_root, state.contentDescription)
        views.setContentDescription(R.id.widget_power_ring, state.contentDescription)
        return views
    }

    private data class Quad(val bg: Int, val badge: Int, val power: Int, val subtitleColor: Int)
}

/**
 * Coordinates pushing state updates to all placed WeMi Home Screen widgets and initiating
 * one-tap launcher pinning (`requestPinAppWidget`) from inside the app.
 */
object WidgetUpdater {
    fun getActiveWidgetIds(context: Context): IntArray {
        val manager = AppWidgetManager.getInstance(context.applicationContext) ?: return IntArray(0)
        val component = ComponentName(context.applicationContext, WeMiSwitchWidgetProvider::class.java)
        return manager.getAppWidgetIds(component) ?: IntArray(0)
    }

    fun refreshWidgetsForDevices(
        context: Context,
        devices: List<DeviceEntity>,
        updatingWidgetId: Int? = null
    ) {
        val appCtx = context.applicationContext
        val manager = AppWidgetManager.getInstance(appCtx) ?: return
        val ids = getActiveWidgetIds(appCtx)
        if (ids.isEmpty()) return
        ids.forEach { widgetId ->
            val config = WidgetConfigStore.load(appCtx, widgetId)
            val views = WidgetRemoteViewsBuilder.buildRemoteViews(
                context = appCtx,
                appWidgetId = widgetId,
                config = config,
                devices = devices,
                isUpdating = widgetId == updatingWidgetId
            )
            runCatching { manager.updateAppWidget(widgetId, views) }
        }
    }

    fun updateSingleWidget(
        context: Context,
        appWidgetId: Int,
        config: WidgetTargetConfig?,
        devices: List<DeviceEntity>,
        isUpdating: Boolean = false
    ) {
        val appCtx = context.applicationContext
        val manager = AppWidgetManager.getInstance(appCtx) ?: return
        val views = WidgetRemoteViewsBuilder.buildRemoteViews(
            context = appCtx,
            appWidgetId = appWidgetId,
            config = config,
            devices = devices,
            isUpdating = isUpdating
        )
        runCatching { manager.updateAppWidget(appWidgetId, views) }
    }

    fun requestPinDeviceWidget(
        context: Context,
        device: DeviceEntity,
        allDevices: List<DeviceEntity>
    ): String = requestPinWidget(
        context = context,
        config = WidgetTargetConfig.forDevice(appWidgetId = 0, device = device),
        devices = allDevices
    )

    fun requestPinRoomWidget(
        context: Context,
        roomName: String,
        networkKey: String,
        allDevices: List<DeviceEntity>
    ): String = requestPinWidget(
        context = context,
        config = WidgetTargetConfig.forRoom(appWidgetId = 0, roomName = roomName, networkKey = networkKey),
        devices = allDevices
    )

    private fun requestPinWidget(
        context: Context,
        config: WidgetTargetConfig,
        devices: List<DeviceEntity>
    ): String {
        val appCtx = context.applicationContext
        val label = config.displayName.ifBlank { config.normalizedRoomName() }
        val manager = AppWidgetManager.getInstance(appCtx)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || manager == null || !manager.isRequestPinAppWidgetSupported) {
            return "Long-press your Home Screen → Widgets → WeMi Switch Control to add \"$label\"."
        }

        val provider = ComponentName(appCtx, WeMiSwitchWidgetProvider::class.java)
        val pinnedCallbackIntent = Intent(appCtx, WeMiSwitchWidgetProvider::class.java).apply {
            action = WeMiSwitchWidgetProvider.ACTION_WIDGET_PINNED
            putExtra(WeMiSwitchWidgetProvider.EXTRA_TARGET_TYPE, config.targetType.name)
            putExtra(WeMiSwitchWidgetProvider.EXTRA_DEVICE_ID, config.deviceId)
            putExtra(WeMiSwitchWidgetProvider.EXTRA_SERIAL_NUMBER, config.serialNumber)
            putExtra(WeMiSwitchWidgetProvider.EXTRA_UDN, config.udn)
            putExtra(WeMiSwitchWidgetProvider.EXTRA_ROOM_NAME, config.roomName)
            putExtra(WeMiSwitchWidgetProvider.EXTRA_NETWORK_KEY, config.networkKey)
            putExtra(WeMiSwitchWidgetProvider.EXTRA_DISPLAY_NAME, config.displayName)
        }
        val requestCode = ("${config.targetType.name}:${config.deviceId}:${config.roomName}:${config.networkKey}")
            .hashCode() and 0x7FFFFFFF
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val successCallback = PendingIntent.getBroadcast(appCtx, requestCode, pinnedCallbackIntent, flags)

        val previewViews = WidgetRemoteViewsBuilder.buildPreviewRemoteViews(appCtx, config, devices)
        val extras = Bundle().apply {
            putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, previewViews)
        }
        val accepted = runCatching {
            manager.requestPinAppWidget(provider, extras, successCallback)
        }.getOrDefault(false)

        return if (accepted) {
            "Confirm on your Home Screen to pin \"$label\" widget."
        } else {
            "Long-press your Home Screen → Widgets → WeMi Switch Control to add \"$label\"."
        }
    }
}
