package org.suppressingfire.wemi.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android Home Screen AppWidgetProvider for one-tap Light & Room power control without opening
 * the app. Uses `goAsync()` with a bounded `8.5s` timeout so local-LAN UPnP SOAP toggles complete
 * cleanly inside the broadcast lifecycle without risking an ANR.
 */
class WeMiSwitchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                withTimeoutOrNull(BROADCAST_TIMEOUT_MS) {
                    WidgetActionExecutor.refreshWidgetIds(context, appWidgetIds)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_WIDGET -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val pendingResult = goAsync()
                widgetScope.launch {
                    try {
                        withTimeoutOrNull(BROADCAST_TIMEOUT_MS) {
                            WidgetActionExecutor.toggleWidgetTarget(context, appWidgetId)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_WIDGET_PINNED -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                val targetType = WidgetTargetType.fromRaw(intent.getStringExtra(EXTRA_TARGET_TYPE))
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || targetType == null) return

                val config = WidgetTargetConfig(
                    appWidgetId = appWidgetId,
                    targetType = targetType,
                    deviceId = intent.getIntExtra(EXTRA_DEVICE_ID, 0),
                    serialNumber = intent.getStringExtra(EXTRA_SERIAL_NUMBER).orEmpty(),
                    roomName = intent.getStringExtra(EXTRA_ROOM_NAME).orEmpty(),
                    networkKey = intent.getStringExtra(EXTRA_NETWORK_KEY).orEmpty(),
                    displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
                )
                WidgetConfigStore.save(context, config)

                val pendingResult = goAsync()
                widgetScope.launch {
                    try {
                        withTimeoutOrNull(BROADCAST_TIMEOUT_MS) {
                            WidgetActionExecutor.refreshWidgetIds(context, intArrayOf(appWidgetId))
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            WidgetConfigStore.delete(context, widgetId)
        }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        WidgetConfigStore.remapIds(context, oldWidgetIds, newWidgetIds)
    }

    companion object {
        const val ACTION_TOGGLE_WIDGET = "org.suppressingfire.wemi.widget.ACTION_TOGGLE_WIDGET"
        const val ACTION_WIDGET_PINNED = "org.suppressingfire.wemi.widget.ACTION_WIDGET_PINNED"

        const val EXTRA_TARGET_TYPE = "extra_widget_target_type"
        const val EXTRA_DEVICE_ID = "extra_widget_device_id"
        const val EXTRA_SERIAL_NUMBER = "extra_widget_serial_number"
        const val EXTRA_ROOM_NAME = "extra_widget_room_name"
        const val EXTRA_NETWORK_KEY = "extra_widget_network_key"
        const val EXTRA_DISPLAY_NAME = "extra_widget_display_name"

        private const val BROADCAST_TIMEOUT_MS = 8_500L
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
