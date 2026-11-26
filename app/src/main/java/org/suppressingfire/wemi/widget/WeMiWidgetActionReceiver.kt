package org.suppressingfire.wemi.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import org.suppressingfire.wemi.network.LanRuntimeUtils

/**
 * Non-exported (`android:exported="false"`) [BroadcastReceiver] dedicated exclusively to
 * app-originated Home Screen widget `PendingIntent` callbacks (`ACTION_TOGGLE_WIDGET` and
 * `ACTION_WIDGET_PINNED`).
 *
 * Isolates privileged toggle and pin-binding actions away from the exported
 * [WeMiSwitchWidgetProvider] so arbitrary third-party apps on the device cannot spoof
 * `ACTION_TOGGLE_WIDGET` or forge `ACTION_WIDGET_PINNED` broadcasts.
 */
class WeMiWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WeMiSwitchWidgetProvider.ACTION_TOGGLE_WIDGET -> handleToggle(context, intent)
            WeMiSwitchWidgetProvider.ACTION_WIDGET_PINNED -> handlePinned(context, intent)
        }
    }

    private fun handleToggle(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId <= 0) return
        val activeIds = WidgetUpdater.getActiveWidgetIds(context)
        if (appWidgetId !in activeIds) return

        WeMiSwitchWidgetProvider.dispatchAsyncBroadcast(
            receiver = this,
            context = context,
            actionLabel = "Toggle($appWidgetId)"
        ) {
            WidgetActionExecutor.toggleWidgetTarget(
                context = context,
                appWidgetId = appWidgetId,
                repositoryOverride = WeMiSwitchWidgetProvider.repositoryProviderForTests?.invoke(context)
            )
        }
    }

    private fun handlePinned(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val targetType = WidgetTargetType.fromRaw(
            intent.getStringExtra(WeMiSwitchWidgetProvider.EXTRA_TARGET_TYPE)
        )
        if (appWidgetId <= 0 || targetType == null) return

        val nonce = intent.getStringExtra(EXTRA_PIN_NONCE)
        if (!consumePendingPinNonce(nonce)) return

        val activeIds = WidgetUpdater.getActiveWidgetIds(context)
        if (appWidgetId !in activeIds) return

        val rawSerial = intent.getStringExtra(WeMiSwitchWidgetProvider.EXTRA_SERIAL_NUMBER).orEmpty()
        val rawUdn = intent.getStringExtra(WeMiSwitchWidgetProvider.EXTRA_UDN).orEmpty()
        val config = WidgetTargetConfig(
            appWidgetId = appWidgetId,
            targetType = targetType,
            deviceId = intent.getIntExtra(WeMiSwitchWidgetProvider.EXTRA_DEVICE_ID, 0),
            serialNumber = rawSerial.takeUnless { LanRuntimeUtils.isPlaceholderSerial(it) }.orEmpty(),
            roomName = intent.getStringExtra(WeMiSwitchWidgetProvider.EXTRA_ROOM_NAME).orEmpty(),
            networkKey = intent.getStringExtra(WeMiSwitchWidgetProvider.EXTRA_NETWORK_KEY).orEmpty(),
            displayName = intent.getStringExtra(WeMiSwitchWidgetProvider.EXTRA_DISPLAY_NAME).orEmpty(),
            udn = LanRuntimeUtils.canonicalHardwareUdn(rawUdn)
        )
        WidgetConfigStore.save(context, config)

        WeMiSwitchWidgetProvider.dispatchAsyncBroadcast(
            receiver = this,
            context = context,
            actionLabel = "Pinned($appWidgetId)"
        ) {
            WidgetActionExecutor.refreshWidgetIds(
                context = context,
                appWidgetIds = intArrayOf(appWidgetId),
                repositoryOverride = WeMiSwitchWidgetProvider.repositoryProviderForTests?.invoke(context)
            )
        }
    }

    companion object {
        const val EXTRA_PIN_NONCE = "extra_widget_pin_nonce"

        private const val PIN_NONCE_TTL_MS = 10 * 60 * 1000L
        private const val MAX_PENDING_PIN_NONCES = 16
        private val secureRandom = SecureRandom()
        private val pendingPinNonces = ConcurrentHashMap<String, Long>()

        /**
         * Mints and registers a single-use 128-bit cryptographic hex nonce attached to an
         * `AppWidgetManager.requestPinAppWidget` callback `PendingIntent`.
         */
        internal fun issuePendingPinNonce(nowMs: Long = System.currentTimeMillis()): String {
            pruneExpiredNonces(nowMs)
            while (pendingPinNonces.size >= MAX_PENDING_PIN_NONCES) {
                val oldestKey = pendingPinNonces.entries.minByOrNull { it.value }?.key ?: break
                pendingPinNonces.remove(oldestKey)
            }
            val bytes = ByteArray(16)
            secureRandom.nextBytes(bytes)
            val nonce = buildString(32) {
                for (b in bytes) {
                    val v = b.toInt() and 0xFF
                    append("0123456789abcdef"[v ushr 4])
                    append("0123456789abcdef"[v and 0x0F])
                }
            }
            pendingPinNonces[nonce] = nowMs + PIN_NONCE_TTL_MS
            return nonce
        }

        /**
         * Atomically validates and consumes [nonce], returning `true` only once while unexpired.
         */
        internal fun consumePendingPinNonce(
            nonce: String?,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            pruneExpiredNonces(nowMs)
            val clean = nonce?.trim().orEmpty()
            if (clean.length != 32) return false
            val expiresAt = pendingPinNonces.remove(clean) ?: return false
            return expiresAt >= nowMs
        }

        internal fun clearPendingPinNoncesForTests() {
            pendingPinNonces.clear()
        }

        private fun pruneExpiredNonces(nowMs: Long) {
            val iter = pendingPinNonces.entries.iterator()
            while (iter.hasNext()) {
                if (iter.next().value < nowMs) iter.remove()
            }
        }
    }
}
