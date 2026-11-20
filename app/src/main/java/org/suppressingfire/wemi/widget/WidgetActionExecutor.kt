package org.suppressingfire.wemi.widget

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository

/**
 * Headless coroutine executor invoked directly by [WeMiSwitchWidgetProvider] when the user
 * taps a Home Screen light or room widget, executing real local-LAN UPnP SOAP toggles without
 * launching the app UI.
 */
object WidgetActionExecutor {

    private suspend fun loadAllDevices(repository: SwitchRepository): List<DeviceEntity> {
        if (repository.isDemoShowroomMode.value) {
            repository.ensureSeeded()
        }
        return repository.allDevices.first()
    }

    /**
     * Toggles the light or room bound to [appWidgetId] and refreshes all placed WeMi widgets
     * with the resulting hardware/Room state.
     *
     * @param repositoryOverride Optional repository injection for hermetic loopback tests.
     */
    suspend fun toggleWidgetTarget(
        context: Context,
        appWidgetId: Int,
        repositoryOverride: SwitchRepository? = null
    ): Boolean {
        val appCtx = context.applicationContext
        val config = WidgetConfigStore.load(appCtx, appWidgetId) ?: return false

        val ownsRepo = repositoryOverride == null
        val repository = repositoryOverride ?: SwitchRepository(
            dao = SwitchDatabase.getDatabase(appCtx).switchDao(),
            context = appCtx
        )

        return try {
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
                        // Heal persisted config if device ID or name changed
                        if (updated.id != config.deviceId || updated.name != config.displayName) {
                            WidgetConfigStore.save(
                                appCtx,
                                WidgetTargetConfig.forDevice(appWidgetId, updated)
                            )
                        }
                        true
                    } else {
                        false
                    }
                }

                WidgetTargetType.ROOM -> {
                    val roomSwitches = WidgetRemoteViewsBuilder.resolveRoomDevices(config, currentDevices)
                    if (roomSwitches.isEmpty()) {
                        false
                    } else {
                        val reachable = roomSwitches.filter { it.isReachable }
                        if (reachable.isNotEmpty()) {
                            val targetOn = reachable.none { it.isOn }
                            val homeKey = reachable.first().networkKey
                            repository.toggleRoomPower(
                                roomName = config.normalizedRoomName(),
                                targetOn = targetOn,
                                networkKey = homeKey
                            )
                        } else {
                            // All switches in this room were previously marked offline — actively probe/wake them
                            coroutineScope {
                                roomSwitches.map { dev ->
                                    async { repository.setDevicePowerExplicit(dev, true) }
                                }.awaitAll()
                            }
                        }
                        true
                    }
                }
            }

            val updatedDevices = repository.allDevices.first()
            WidgetUpdater.refreshWidgetsForDevices(appCtx, updatedDevices)
            // Also update the tapped ID explicitly in case the launcher/test hasn't registered it in AppWidgetManager
            WidgetUpdater.updateSingleWidget(
                context = appCtx,
                appWidgetId = appWidgetId,
                config = WidgetConfigStore.load(appCtx, appWidgetId) ?: config,
                devices = updatedDevices,
                isUpdating = false
            )
            executed
        } finally {
            if (ownsRepo) {
                repository.close()
            }
        }
    }

    /**
     * Refreshes the supplied [appWidgetIds] against the latest persisted Room device state.
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
            val devices = loadAllDevices(repository)
            appWidgetIds.forEach { widgetId ->
                val config = WidgetConfigStore.load(appCtx, widgetId)
                WidgetUpdater.updateSingleWidget(
                    context = appCtx,
                    appWidgetId = widgetId,
                    config = config,
                    devices = devices,
                    isUpdating = false
                )
            }
        } finally {
            if (ownsRepo) {
                repository.close()
            }
        }
    }
}
