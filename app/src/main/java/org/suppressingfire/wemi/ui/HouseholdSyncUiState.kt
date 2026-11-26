package org.suppressingfire.wemi.ui

import org.suppressingfire.wemi.network.HouseholdSyncBundle
import org.suppressingfire.wemi.network.HouseholdSyncPeer
import org.suppressingfire.wemi.network.HouseholdSyncServerSession

data class PendingHouseholdImport(
    val sourceDescription: String,
    val bundle: HouseholdSyncBundle
)

data class HouseholdSyncUiState(
    val showSendDialog: Boolean = false,
    val showReceiveDialog: Boolean = false,
    val isStartingReceiveSession: Boolean = false,
    val pendingImport: PendingHouseholdImport? = null,
    val discoveredPeers: List<HouseholdSyncPeer> = emptyList(),
    val isDiscoveringPeers: Boolean = false,
    val isPushingToPeer: Boolean = false,
    val sendStatusMessage: String? = null,
    val sendErrorMessage: String? = null,
    val activeReceiveSession: HouseholdSyncServerSession? = null,
    val receiveStatusMessage: String? = null,
    val isImporting: Boolean = false
) {
    val hasAnyDialogOpen: Boolean
        get() = showSendDialog || showReceiveDialog || pendingImport != null
}
