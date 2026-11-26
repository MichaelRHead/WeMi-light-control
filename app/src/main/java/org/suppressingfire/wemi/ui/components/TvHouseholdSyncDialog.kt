package org.suppressingfire.wemi.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.suppressingfire.wemi.network.HouseholdImportMode
import org.suppressingfire.wemi.network.HouseholdSyncCrypto
import org.suppressingfire.wemi.ui.HouseholdSyncUiState
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenPrimary

/**
 * 10-foot Android TV D-Pad overlay for receiving a PIN-encrypted Household Configuration transfer
 * from a phone, tablet, or Chromebook on the same Wi-Fi/LAN network.
 */
@Composable
internal fun TvHouseholdSyncOverlay(
    state: HouseholdSyncUiState,
    onStartNewSession: () -> Unit,
    onConfirmImport: (HouseholdImportMode) -> Unit,
    onPauseSession: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val session = state.activeReceiveSession
    val pending = state.pendingImport
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentPauseSession by rememberUpdatedState(onPauseSession)

    ScopedWindowFlagSecure(enabled = session != null)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                currentPauseSession()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = true) {
        onDismiss()
    }

    var remainingSeconds by remember(session?.expiresAtEpochMs) {
        mutableIntStateOf(
            session?.let { ((it.expiresAtEpochMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0) } ?: 0
        )
    }
    LaunchedEffect(session?.expiresAtEpochMs) {
        if (session == null) return@LaunchedEffect
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds = ((session.expiresAtEpochMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
        }
    }

    val mergeRequester = remember { FocusRequester() }
    val replaceRequester = remember { FocusRequester() }
    val newCodeRequester = remember { FocusRequester() }
    val cancelRequester = remember { FocusRequester() }

    LaunchedEffect(pending != null, session != null) {
        runCatching {
            when {
                pending != null -> mergeRequester.requestFocus()
                session == null -> newCodeRequester.requestFocus()
                else -> cancelRequester.requestFocus()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9040A14))
            .testTag("tv_household_sync_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = TvSurfaceCard,
            border = BorderStroke(2.dp, TvCyanFocusBorder),
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth(0.88f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (pending != null) {
                    val bundle = pending.bundle
                    Text(
                        text = "Apply Household Setup?",
                        color = TvTextPrimary,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Text(
                        text = "Received from ${pending.sourceDescription}",
                        color = TvCyanFocusBorder,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${bundle.homes.size} Home(s) • ${bundle.devices.size} Switch(es) across ${bundle.distinctRoomCount} Room(s)",
                        color = TvTextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvSyncActionButton(
                            label = "Merge & Apply",
                            isPrimary = true,
                            focusRequester = mergeRequester,
                            onNavigateRight = { runCatching { replaceRequester.requestFocus() }; true },
                            onClick = { onConfirmImport(HouseholdImportMode.MERGE) },
                            testTag = "tv_sync_confirm_merge_button"
                        )
                        TvSyncActionButton(
                            label = "Replace All",
                            isPrimary = false,
                            focusRequester = replaceRequester,
                            onNavigateLeft = { runCatching { mergeRequester.requestFocus() }; true },
                            onNavigateRight = { runCatching { cancelRequester.requestFocus() }; true },
                            onClick = { onConfirmImport(HouseholdImportMode.REPLACE) },
                            testTag = "tv_sync_confirm_replace_button"
                        )
                        TvSyncActionButton(
                            label = "Cancel",
                            isPrimary = false,
                            focusRequester = cancelRequester,
                            onNavigateLeft = { runCatching { replaceRequester.requestFocus() }; true },
                            onClick = onDismiss,
                            testTag = "tv_sync_cancel_button"
                        )
                    }
                } else {
                    Text(
                        text = "Pair from Phone",
                        color = TvTextPrimary,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Text(
                        text = "On your phone, open More -> Household Config Sync -> Send to TV and enter this one-time code:",
                        color = TvTextSecondary,
                        fontSize = 14.sp
                    )
                    if (session != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TvSurfaceCardActive,
                            border = BorderStroke(1.5.dp, TvCyanFocusBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 16.dp, horizontal = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = HouseholdSyncCrypto.formatPairingCode(session.pairingCode),
                                    color = TvCyanFocusBorder,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 40.sp,
                                    modifier = Modifier.testTag("tv_sync_pairing_code")
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${session.ipAddress}:${session.port}",
                                    color = TvTextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp,
                                    modifier = Modifier.testTag("tv_sync_endpoint")
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val mins = remainingSeconds / 60
                                val secs = (remainingSeconds % 60).toString().padStart(2, '0')
                                Text(
                                    text = "Encrypted Wi-Fi transfer • Expires in $mins:$secs",
                                    color = TvTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else if (state.receiveStatusMessage != null) {
                        Text(
                            text = state.receiveStatusMessage,
                            color = TvCyanFocusBorder,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .semantics { liveRegion = LiveRegionMode.Polite }
                                .testTag("tv_sync_status_message")
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (session == null) {
                            TvSyncActionButton(
                                label = "New Code",
                                isPrimary = true,
                                focusRequester = newCodeRequester,
                                onNavigateRight = { runCatching { cancelRequester.requestFocus() }; true },
                                onClick = onStartNewSession,
                                testTag = "tv_sync_new_code_button"
                            )
                        }
                        TvSyncActionButton(
                            label = "Cancel",
                            isPrimary = false,
                            focusRequester = cancelRequester,
                            onNavigateLeft = if (session == null) {
                                { runCatching { newCodeRequester.requestFocus() }; true }
                            } else {
                                { true }
                            },
                            onClick = onDismiss,
                            testTag = "tv_sync_cancel_button"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSyncActionButton(
    label: String,
    isPrimary: Boolean,
    focusRequester: FocusRequester,
    onNavigateLeft: () -> Boolean = { true },
    onNavigateRight: () -> Boolean = { true },
    onClick: () -> Unit,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }
    val currentClick by rememberUpdatedState(onClick)
    val currentLeft by rememberUpdatedState(onNavigateLeft)
    val currentRight by rememberUpdatedState(onNavigateRight)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = when {
            isFocused && isPrimary -> WemoGreenPrimary
            isFocused -> Color(0xFF133B66)
            isPrimary -> WemoGreenDark
            else -> Color(0xFF0F2540)
        },
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) TvCyanFocusBorder else TvCyanFocusBorder.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { currentClick(); true }
                        Key.DirectionLeft -> currentLeft()
                        Key.DirectionRight -> currentRight()
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
