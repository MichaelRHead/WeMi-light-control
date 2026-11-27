package org.suppressingfire.wemi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.groupDevicesByCanonicalRoom
import org.suppressingfire.wemi.ui.components.TvFocusTarget
import org.suppressingfire.wemi.ui.components.TvFocusedBannerTarget
import org.suppressingfire.wemi.ui.components.reconcileTvFocusState
import org.suppressingfire.wemi.ui.components.resolveTvFocusedBannerTarget

/**
 * Pure JVM unit tests for Android TV canonical room focus targets, top-bar status resolution,
 * and focus recovery across discovery, room canonicalization, and offline transitions.
 */
class TvFocusModelTest {

    @Test
    fun `canonical room header resolution handles case variants, reachable vs offline dimmers, and vanished rooms`() {
        val livingSwitch = DeviceEntity(
            id = 1,
            name = "Living Lamp",
            roomName = "Living Room",
            deviceType = DeviceType.SWITCH_PLUG.name,
            isReachable = true
        )
        val livingDimmer = DeviceEntity(
            id = 2,
            name = "Living Dimmer",
            roomName = "Living Room",
            deviceType = DeviceType.LIGHTBULB_DIMMER.name,
            isReachable = true
        )
        val offlineKitchenDimmer = DeviceEntity(
            id = 3,
            name = "Kitchen Dimmer",
            roomName = "Kitchen",
            deviceType = DeviceType.LIGHTBULB_DIMMER.name,
            isReachable = false
        )
        val hallMotion = DeviceEntity(
            id = 4,
            name = "Hall Motion",
            roomName = "Hallway & Landing",
            deviceType = DeviceType.MOTION_SENSOR.name,
            isReachable = true
        )
        val unassignedPlug = DeviceEntity(
            id = 5,
            name = "Mystery Plug",
            roomName = "",
            deviceType = DeviceType.SWITCH_PLUG.name,
            isReachable = true
        )
        val grouped = groupDevicesByCanonicalRoom(
            listOf(livingSwitch, livingDimmer, offlineKitchenDimmer, hallMotion, unassignedPlug)
        ).toList()
        val ordered = grouped.flatMap { it.second }

        assertEquals(
            TvFocusedBannerTarget.Room("Living Room", hasReachableDimmers = true),
            resolveTvFocusedBannerTarget(
                activeFocusTarget = TvFocusTarget.RoomHeader.forRoom("living room"),
                groupedByRoom = grouped,
                orderedDevices = ordered,
                lastFocusedDeviceId = livingSwitch.id,
                isRefreshing = false
            )
        )
        assertEquals(
            TvFocusedBannerTarget.Room("Kitchen", hasReachableDimmers = false),
            resolveTvFocusedBannerTarget(
                activeFocusTarget = TvFocusTarget.RoomHeader.forRoom("kitchen"),
                groupedByRoom = grouped,
                orderedDevices = ordered,
                lastFocusedDeviceId = livingSwitch.id,
                isRefreshing = false
            )
        )
        assertEquals(
            TvFocusedBannerTarget.Room("Unassigned", hasReachableDimmers = false),
            resolveTvFocusedBannerTarget(
                activeFocusTarget = TvFocusTarget.RoomHeader.forRoom("  "),
                groupedByRoom = grouped,
                orderedDevices = ordered,
                lastFocusedDeviceId = livingSwitch.id,
                isRefreshing = false
            )
        )
        assertEquals(
            TvFocusedBannerTarget.Device(offlineKitchenDimmer, isDimming = false),
            resolveTvFocusedBannerTarget(
                activeFocusTarget = TvFocusTarget.DeviceSlider(offlineKitchenDimmer.id),
                groupedByRoom = grouped,
                orderedDevices = ordered,
                lastFocusedDeviceId = livingSwitch.id,
                isRefreshing = false
            )
        )
        assertEquals(
            TvFocusedBannerTarget.Device(hallMotion, isDimming = false),
            resolveTvFocusedBannerTarget(
                activeFocusTarget = TvFocusTarget.RoomHeader.forRoom("Garage"),
                groupedByRoom = grouped,
                orderedDevices = ordered,
                lastFocusedDeviceId = hallMotion.id,
                isRefreshing = false
            )
        )
    }

    @Test
    fun `reconcileTvFocusState preserves upgraded canonical rooms and recovers vanished rooms or offline sliders`() {
        val livingSwitch = DeviceEntity(
            id = 1,
            name = "Living Lamp",
            roomName = "Living Room",
            deviceType = DeviceType.SWITCH_PLUG.name,
            isReachable = true
        )
        val offlineDimmer = DeviceEntity(
            id = 2,
            name = "Kitchen Dimmer",
            roomName = "Kitchen",
            deviceType = DeviceType.LIGHTBULB_DIMMER.name,
            isReachable = false
        )
        val grouped = groupDevicesByCanonicalRoom(listOf(livingSwitch, offlineDimmer)).toList()
        val ordered = grouped.flatMap { it.second }

        // Case-variant room header upgrade ("kitchen" -> "Kitchen") keeps focus without detaching
        val upgradedRoom = reconcileTvFocusState(
            activeFocusTarget = TvFocusTarget.RoomHeader.forRoom("kitchen"),
            lastFocusedDeviceId = 999,
            didRequestInitialGridFocus = true,
            groupedByRoom = grouped,
            orderedDevices = ordered,
            hasHolidayDevices = false,
            holidayChipVanishedWhileFocused = false
        )
        assertEquals(TvFocusTarget.RoomHeader.forRoom("Kitchen"), upgradedRoom.nextActiveTarget)
        assertEquals(livingSwitch.id, upgradedRoom.nextLastFocusedDeviceId)
        assertFalse(upgradedRoom.shouldRequestFocus)

        // Vanished focused room recovers cleanly to lastFocusedDeviceId instead of stranding focus
        val vanishedRoom = reconcileTvFocusState(
            activeFocusTarget = TvFocusTarget.RoomHeader.forRoom("Garage"),
            lastFocusedDeviceId = offlineDimmer.id,
            didRequestInitialGridFocus = true,
            groupedByRoom = grouped,
            orderedDevices = ordered,
            hasHolidayDevices = false,
            holidayChipVanishedWhileFocused = false
        )
        assertEquals(TvFocusTarget.DeviceToggle(offlineDimmer.id), vanishedRoom.nextActiveTarget)
        assertEquals(offlineDimmer.id, vanishedRoom.nextLastFocusedDeviceId)
        assertTrue(vanishedRoom.shouldRequestFocus)
        assertTrue(vanishedRoom.awaitFrameBeforeFocus)

        // Offline dimmer slider demotes to DeviceToggle on the same card so OK offers Reconnect
        val offlineSlider = reconcileTvFocusState(
            activeFocusTarget = TvFocusTarget.DeviceSlider(offlineDimmer.id),
            lastFocusedDeviceId = offlineDimmer.id,
            didRequestInitialGridFocus = true,
            groupedByRoom = grouped,
            orderedDevices = ordered,
            hasHolidayDevices = false,
            holidayChipVanishedWhileFocused = false
        )
        assertEquals(TvFocusTarget.DeviceToggle(offlineDimmer.id), offlineSlider.nextActiveTarget)
        assertTrue(offlineSlider.shouldRequestFocus)

        // Empty home falls back to TopAction(3) (Rescan)
        val emptyHome = reconcileTvFocusState(
            activeFocusTarget = TvFocusTarget.RoomHeader.forRoom("Living Room"),
            lastFocusedDeviceId = livingSwitch.id,
            didRequestInitialGridFocus = true,
            groupedByRoom = emptyList(),
            orderedDevices = emptyList(),
            hasHolidayDevices = false,
            holidayChipVanishedWhileFocused = false
        )
        assertEquals(TvFocusTarget.TopAction(3), emptyHome.nextActiveTarget)
        assertEquals(null, emptyHome.nextLastFocusedDeviceId)
        assertFalse(emptyHome.nextDidRequestInitialGridFocus)
        assertTrue(emptyHome.shouldRequestFocus)
    }

    @Test
    fun `demoted offline slider, missing slider fallback, and Holiday ON banner target guard behave deterministically`() {
        val firstSwitch = DeviceEntity(
            id = 10,
            name = "Entry Switch",
            roomName = "Entry",
            deviceType = DeviceType.SWITCH_PLUG.name,
            isReachable = true
        )
        val offlineDimmer = DeviceEntity(
            id = 11,
            name = "Dining Dimmer",
            roomName = "Dining",
            deviceType = DeviceType.LIGHTBULB_DIMMER.name,
            isReachable = false
        )
        val grouped = groupDevicesByCanonicalRoom(listOf(firstSwitch, offlineDimmer)).toList()
        val ordered = grouped.flatMap { it.second }

        // Even if Compose's detach fallback already overwrote activeFocusTarget to TopAction(0),
        // focusedSliderDemotedDeviceId restores focus to the offline dimmer's DeviceToggle so OK offers Reconnect
        val detachedOfflineSlider = reconcileTvFocusState(
            activeFocusTarget = TvFocusTarget.TopAction(0),
            lastFocusedDeviceId = offlineDimmer.id,
            didRequestInitialGridFocus = true,
            groupedByRoom = grouped,
            orderedDevices = ordered,
            hasHolidayDevices = false,
            holidayChipVanishedWhileFocused = false,
            focusedSliderDemotedDeviceId = offlineDimmer.id
        )
        assertEquals(TvFocusTarget.DeviceToggle(offlineDimmer.id), detachedOfflineSlider.nextActiveTarget)
        assertEquals(offlineDimmer.id, detachedOfflineSlider.nextLastFocusedDeviceId)
        assertTrue(detachedOfflineSlider.shouldRequestFocus)
        assertTrue(detachedOfflineSlider.awaitFrameBeforeFocus)

        // Missing slider respects valid lastFocusedDeviceId consistently with RoomHeader/DeviceToggle
        val missingSlider = reconcileTvFocusState(
            activeFocusTarget = TvFocusTarget.DeviceSlider(999),
            lastFocusedDeviceId = offlineDimmer.id,
            didRequestInitialGridFocus = true,
            groupedByRoom = grouped,
            orderedDevices = ordered,
            hasHolidayDevices = false,
            holidayChipVanishedWhileFocused = false
        )
        assertEquals(TvFocusTarget.DeviceToggle(offlineDimmer.id), missingSlider.nextActiveTarget)
        assertEquals(offlineDimmer.id, missingSlider.nextLastFocusedDeviceId)
        assertTrue(missingSlider.shouldRequestFocus)
        assertTrue(missingSlider.awaitFrameBeforeFocus)

        // Holiday ON top-bar target falls back cleanly to Movie 20% when no holiday devices exist
        assertEquals(
            TvFocusedBannerTarget.TopAction(1, "Holiday ON", "• OK Run Scene"),
            resolveTvFocusedBannerTarget(
                activeFocusTarget = TvFocusTarget.TopAction(1),
                groupedByRoom = grouped,
                orderedDevices = ordered,
                lastFocusedDeviceId = firstSwitch.id,
                isRefreshing = false,
                hasHolidayDevices = true
            )
        )
        assertEquals(
            TvFocusedBannerTarget.TopAction(0, "Movie 20%", "• OK Run Scene"),
            resolveTvFocusedBannerTarget(
                activeFocusTarget = TvFocusTarget.TopAction(1),
                groupedByRoom = grouped,
                orderedDevices = ordered,
                lastFocusedDeviceId = firstSwitch.id,
                isRefreshing = false,
                hasHolidayDevices = false
            )
        )
        assertEquals(
            TvFocusedBannerTarget.TopAction(3, "Scanning...", "• OK Scan LAN"),
            resolveTvFocusedBannerTarget(
                activeFocusTarget = TvFocusTarget.TopAction(3),
                groupedByRoom = grouped,
                orderedDevices = ordered,
                lastFocusedDeviceId = firstSwitch.id,
                isRefreshing = true,
                hasHolidayDevices = false
            )
        )
        assertEquals(
            TvFocusedBannerTarget.TopAction(4, "Sync", "• OK Pair Phone"),
            resolveTvFocusedBannerTarget(
                activeFocusTarget = TvFocusTarget.TopAction(4),
                groupedByRoom = grouped,
                orderedDevices = ordered,
                lastFocusedDeviceId = firstSwitch.id,
                isRefreshing = false,
                hasHolidayDevices = false
            )
        )
    }
}
