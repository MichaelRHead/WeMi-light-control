package org.suppressingfire.wemi.widget

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.SharedSoapLogStore

/**
 * Headless coroutine executor invoked directly by [WeMiSwitchWidgetProvider] when the user
 * taps a Home Screen light or room widget, executing real local-LAN UPnP SOAP toggles without
 * launching the app UI.
 */
object WidgetActionExecutor {
    private val widgetMutexes = ConcurrentHashMap<Int, Mutex>()

    internal fun clearWidgetMutex(appWidgetId: Int) {
        widgetMutexes.remove(appWidgetId)
    }

    internal fun hasMutexFor(appWidgetId: Int): Boolean =
        widgetMutexes.containsKey(appWidgetId)

    private fun logWidgetEvent(
        context: Context,
        appWidgetId: Int,
        label: String,
        action: String,
        statusText: String,
        success: Boolean
    ) {
        SharedSoapLogStore.append(
            context = context,
            entry = SoapLogEntry(
                timestamp = SimpleDateFormat("h:mm:ss a", Locale.US).format(Date()),
                deviceName = label,
                endpoint = "widget://$appWidgetId",
                soapAction = action,
                statusText = statusText,
                xmlSnippet = "",
                isLiveLanSuccess = success
            )
        )
    }

    private suspend fun loadAllDevices(repository: SwitchRepository): List<DeviceEntity> {
        if (repository.isDemoShowroomMode.value) {
            repository.ensureSeeded()
        }
        return repository.allDevices.first()
    }

    /**
     * Toggles the light or room bound to [appWidgetId] and refreshes all placed WeMi widgets
     * with the resulting hardware/Room state. Serializes rapid taps on the same widget ID so
     * back-to-back taps read fresh Room state instead of racing separate repositories.
     *
     * @param repositoryOverride Optional repository injection for hermetic loopback tests.
     */
    suspend fun toggleWidgetTarget(
        context: Context,
        appWidgetId: Int,
        repositoryOverride: SwitchRepository? = null
    ): Boolean = widgetMutexes.computeIfAbsent(appWidgetId) { Mutex() }.withLock {
        val appCtx = context.applicationContext
        val config = WidgetConfigStore.load(appCtx, appWidgetId)
        if (config == null) {
            logWidgetEvent(
                context = appCtx,
                appWidgetId = appWidgetId,
                label = "Widget #$appWidgetId",
                action = "Widget#Toggle",
                statusText = "Ignored • Widget not configured",
                success = false
            )
            return@withLock false
        }

        val ownsRepo = repositoryOverride == null
        val repository = repositoryOverride ?: SwitchRepository(
            dao = SwitchDatabase.getDatabase(appCtx).switchDao(),
            context = appCtx
        )

        try {
            val currentDevices = loadAllDevices(repository)
            // Push instant visual feedback on the tapped widget before LAN SOAP round-trip
            WidgetUpdater.updateSingleWidget(
                context = appCtx,
                appWidgetId = appWidgetId,
                config = config,
                devices = currentDevices,
                isUpdating = true
            )

            val executed = when (config.targetType) {
                WidgetTargetType.DEVICE -> {
                    val targetDevice = WidgetRemoteViewsBuilder.resolveTargetDevice(config, currentDevices)
                    if (targetDevice != null) {
                        val updated = repository.toggleDevicePower(targetDevice)
                        val learnedRealSerial =
                            !LanRuntimeUtils.isPlaceholderSerial(updated.serialNumber) &&
                                !updated.serialNumber.equals(config.serialNumber, ignoreCase = true)
                        val updatedCanonicalUdn = LanRuntimeUtils.canonicalHardwareUdn(updated.udn)
                        val learnedCanonicalUdn = updatedCanonicalUdn.isNotEmpty() &&
                            !updatedCanonicalUdn.equals(LanRuntimeUtils.canonicalHardwareUdn(config.udn), ignoreCase = true)
                        // Heal persisted config if device ID, name, real serial, or canonical UDN changed
                        if (updated.id != config.deviceId ||
                            updated.name != config.displayName ||
                            learnedRealSerial ||
                            learnedCanonicalUdn
                        ) {
                            WidgetConfigStore.save(
                                appCtx,
                                WidgetTargetConfig.forDevice(appWidgetId, updated)
                            )
                        }
                        val stateLabel = when {
                            !updated.isReachable -> "OFFLINE"
                            updated.isOn -> "ON"
                            else -> "OFF"
                        }
                        logWidgetEvent(
                            context = appCtx,
                            appWidgetId = appWidgetId,
                            label = "Widget #$appWidgetId (${updated.name})",
                            action = "Widget#Toggle(DEVICE)",
                            statusText = "Resolved #${updated.id} (${updated.ipAddress}:${updated.port}) -> $stateLabel",
                            success = updated.isReachable
                        )
                        true
                    } else {
                        logWidgetEvent(
                            context = appCtx,
                            appWidgetId = appWidgetId,
                            label = "Widget #$appWidgetId (${config.displayName.ifBlank { "Device" }})",
                            action = "Widget#Toggle(DEVICE)",
                            statusText = "Orphaned • No matching switch (devId=${config.deviceId}, sn=${config.serialNumber.ifBlank { "none" }})",
                            success = false
                        )
                        false
                    }
                }

                WidgetTargetType.ROOM -> {
                    val roomSwitches = WidgetRemoteViewsBuilder.resolveRoomDevices(config, currentDevices)
                    if (roomSwitches.isEmpty()) {
                        logWidgetEvent(
                            context = appCtx,
                            appWidgetId = appWidgetId,
                            label = "Widget #$appWidgetId (${config.normalizedRoomName()})",
                            action = "Widget#Toggle(ROOM)",
                            statusText = "Orphaned • No controllable switches in \"${config.normalizedRoomName()}\" (home=${config.networkKey.ifBlank { "none" }})",
                            success = false
                        )
                        false
                    } else {
                        val resolvedHomeKey = roomSwitches.first().networkKey
                        val reachable = roomSwitches.filter { it.isReachable }
                        if (reachable.isNotEmpty()) {
                            val targetOn = reachable.none { it.isOn }
                            repository.toggleRoomPower(
                                roomName = config.normalizedRoomName(),
                                targetOn = targetOn,
                                networkKey = resolvedHomeKey
                            )
                        } else {
                            // All switches in this room were previously marked offline — actively probe/wake them
                            coroutineScope {
                                roomSwitches.map { dev ->
                                    async { repository.setDevicePowerExplicit(dev, true) }
                                }.awaitAll()
                            }
                        }
                        if (resolvedHomeKey.isNotBlank() && resolvedHomeKey != config.networkKey) {
                            WidgetConfigStore.save(appCtx, config.copy(networkKey = resolvedHomeKey))
                        }
                        logWidgetEvent(
                            context = appCtx,
                            appWidgetId = appWidgetId,
                            label = "Widget #$appWidgetId (${config.normalizedRoomName()})",
                            action = "Widget#Toggle(ROOM)",
                            statusText = "Updated ${roomSwitches.size} switch(es) in \"${config.normalizedRoomName()}\" (home=$resolvedHomeKey)",
                            success = true
                        )
                        true
                    }
                }
            }

            val updatedDevices = repository.allDevices.first()
            val activeIds = WidgetUpdater.getActiveWidgetIds(appCtx)
            WidgetUpdater.refreshWidgetsForDevices(appCtx, updatedDevices)
            // Only update the tapped ID explicitly if the launcher/test hasn't registered it in AppWidgetManager
            if (appWidgetId !in activeIds) {
                WidgetUpdater.updateSingleWidget(
                    context = appCtx,
                    appWidgetId = appWidgetId,
                    config = WidgetConfigStore.load(appCtx, appWidgetId) ?: config,
                    devices = updatedDevices,
                    isUpdating = false
                )
            }
            executed
        } finally {
            if (ownsRepo) {
                repository.close()
            }
        }
    }

    /**
     * Refreshes the supplied [appWidgetIds] against the latest persisted Room device state,
     * serializing each widget ID against in-flight [toggleWidgetTarget] runs so stale toggle
     * snapshots cannot overwrite a subsequent `ACTION_APPWIDGET_UPDATE` refresh.
     */
    suspend fun refreshWidgetIds(
        context: Context,
        appWidgetIds: IntArray,
        repositoryOverride: SwitchRepository? = null
    ) {
        if (appWidgetIds.isEmpty()) return
        val appCtx = context.applicationContext
        val ownsRepo = repositoryOverride == null
        val repository = repositoryOverride ?: SwitchRepository(
            dao = SwitchDatabase.getDatabase(appCtx).switchDao(),
            context = appCtx
        )
        try {
            appWidgetIds.distinct().forEach { widgetId ->
                if (widgetId <= 0) return@forEach
                val refreshSingle: suspend () -> Unit = {
                    val devices = loadAllDevices(repository)
                    val config = WidgetConfigStore.load(appCtx, widgetId)
                    WidgetUpdater.updateSingleWidget(
                        context = appCtx,
                        appWidgetId = widgetId,
                        config = config,
                        devices = devices,
                        isUpdating = false
                    )
                }
                val mutex = if (WidgetConfigStore.load(appCtx, widgetId) != null) {
                    widgetMutexes.computeIfAbsent(widgetId) { Mutex() }
                } else {
                    widgetMutexes[widgetId]
                }
                if (mutex != null) {
                    mutex.withLock { refreshSingle() }
                } else {
                    refreshSingle()
                }
            }
        } finally {
            if (ownsRepo) {
                repository.close()
            }
        }
    }
}
