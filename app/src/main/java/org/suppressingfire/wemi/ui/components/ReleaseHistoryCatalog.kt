package org.suppressingfire.wemi.ui.components

data class ReleaseHistoryEntry(
    val version: String,
    val releaseDate: String,
    val headline: String,
    val summary: String
)

val WEMI_RELEASE_HISTORY: List<ReleaseHistoryEntry> = listOf(
    ReleaseHistoryEntry(
        version = "v36.0",
        releaseDate = "Nov 25, 2026",
        headline = "Live On-Device Switch Rules, Clock Sync & Backup Restore",
        summary = "Create, edit, toggle, and delete native schedules (with Sunrise/Sunset offsets), Auto-Off delays, Timed Actions, and Long Press targets directly in compatible switch memory—complete with automatic clock sync, offline solar city/coordinate setup, automatic pre-write backups with one-tap restore, masked Wi-Fi setup passwords, and a unified On-Switch Rules tab."
    ),
    ReleaseHistoryEntry(
        version = "v35.0",
        releaseDate = "Nov 22, 2026",
        headline = "On-Device Switch Rules Inspector & Refreshed Guides",
        summary = "Inspect existing schedules, Auto-Off delays, Away Mode, and Long Press targets stored directly inside compatible switches from the collapsible Rules tab or any switch's expanded drawer—with zero-delay capability detection, clock-sync alerts, clear Read-Only vs. App-Run separation, and refreshed User Guides including Home Screen Widgets."
    ),
    ReleaseHistoryEntry(
        version = "v34.0",
        releaseDate = "Nov 21, 2026",
        headline = "Chromebook (ChromeOS) Local-LAN Discovery & Refreshed Visuals",
        summary = "Added full ChromeOS (ARCVM) local Wi-Fi switch discovery and control with network-bound multicast, automatic physical-subnet fallback when routers block multicast, a guided manual subnet scanner, safe virtual-subnet home hygiene, and refreshed Play Store & TV visuals."
    ),
    ReleaseHistoryEntry(
        version = "v33.0",
        releaseDate = "Nov 21, 2026",
        headline = "In-App Release History, Cleaner UI & Smarter TV Scenes",
        summary = "Added the subtle top-bar version pill and in-app Release History dialog, removed obsolete clutter across tabs and dialogs, added a confirmation prompt before resetting homes, made Android TV Holiday ON conditional with D-Pad focus recovery, and sharpened room, icon, and setup diagnostics."
    ),
    ReleaseHistoryEntry(
        version = "v32.0",
        releaseDate = "Nov 21, 2026",
        headline = "Stronger Discovery, Rules & Widget Diagnostics",
        summary = "Improved hardware identity matching, clean XML switch names, unified room grouping across phone/TV/widgets, isolated live push events, safer per-home rules, and widget packet diagnostics."
    ),
    ReleaseHistoryEntry(
        version = "v31.0",
        releaseDate = "Nov 19–20, 2026",
        headline = "Home Screen Light & Room Widgets",
        summary = "Pin individual lights or entire rooms to your Android home screen for instant local Wi-Fi toggles without opening the app, plus smarter motion dimmer rules and faster discovery switching."
    ),
    ReleaseHistoryEntry(
        version = "v28.0",
        releaseDate = "Nov 15–19, 2026",
        headline = "Android TV Inline Dimming & Faster Push Sync",
        summary = "Added focusable inline and room-header dimmer sliders on Android TV, deterministic remote navigation across all columns, instant wall-switch push sync on adoption, and hardened setup diagnostics."
    ),
    ReleaseHistoryEntry(
        version = "v25.0",
        releaseDate = "Nov 14, 2026",
        headline = "One-Tap All Off Header Button",
        summary = "Added an All Off button right beside Refresh in the top bar to turn off every reachable light and switch on your active Wi-Fi Home in one tap."
    ),
    ReleaseHistoryEntry(
        version = "v24.0",
        releaseDate = "Nov 11, 2026",
        headline = "Smaller, Faster Release Bundle",
        summary = "Enabled full release code and resource optimization for faster startup and a streamlined pure-Kotlin app footprint."
    ),
    ReleaseHistoryEntry(
        version = "v23.0",
        releaseDate = "Nov 10, 2026",
        headline = "Redesigned 3-Column TV Dashboard & Compact Header",
        summary = "Introduced the 3-column room-grouped Android TV grid with unmistakable cyan focus highlights and condensed the mobile top header into a single row."
    ),
    ReleaseHistoryEntry(
        version = "v22.0",
        releaseDate = "Nov 10, 2026",
        headline = "Ocean Teal Visual Refresh & Lightweight Networking",
        summary = "Unified the Ocean Teal, Electric Cyan, and Deep Sapphire theme across phones, tablets, and TVs while moving to a fast first-party local networking engine."
    ),
    ReleaseHistoryEntry(
        version = "v21.0",
        releaseDate = "Nov 9–10, 2026",
        headline = "Native Form-Factor Layouts & Open Source Notices",
        summary = "Dedicated automatic phone/tablet and Android TV layouts, in-app MIT open source license notices on the More tab, and clean hardware compatibility labeling."
    ),
    ReleaseHistoryEntry(
        version = "v20.0",
        releaseDate = "Nov 9, 2026",
        headline = "9-Port Self-Healing & Smarter Room Grouping",
        summary = "Automatic 9-port self-healing across every switch command, cellular-safe Wi-Fi binding, instant offline recovery, and CamelCase/Ensuite automatic room matching."
    ),
    ReleaseHistoryEntry(
        version = "v19.0",
        releaseDate = "Nov 9, 2026",
        headline = "One-Tap Troubleshooting & Telemetry Export",
        summary = "Added privacy-first diagnostic report sharing (never including Wi-Fi passwords) to help troubleshoot stubborn switches quickly."
    ),
    ReleaseHistoryEntry(
        version = "v18.0",
        releaseDate = "Nov 9, 2026",
        headline = "Accessibility & Back-Navigation Polish",
        summary = "Standardized generous 48dp+ touch targets, descriptive TalkBack switch announcements, and predictable Android Back-button stepping."
    ),
    ReleaseHistoryEntry(
        version = "v17.0",
        releaseDate = "Nov 8–9, 2026",
        headline = "Factory-Reset Setup Reliability & Top-First Dimmers",
        summary = "Upgraded 10.22.22.1 local switch setup with full 11-channel 2.4 GHz scanning, non-destructive password retries, Unassigned room sorting, and top-first dimmer sliders."
    ),
    ReleaseHistoryEntry(
        version = "v16.0",
        releaseDate = "Nov 8, 2026",
        headline = "Compact Home Bars & One-Tap LAN Reset",
        summary = "Streamlined the Wi-Fi Home and discovery banners into single-line bars, added one-tap Reset & Rescan LAN, and filtered out factory setup hotspot names."
    ),
    ReleaseHistoryEntry(
        version = "v15.0",
        releaseDate = "Nov 8, 2026",
        headline = "Auto-Resizing Switch & Room Titles",
        summary = "Long switch and room names now sit on a dedicated top line and scale smoothly so names never clip or wrap awkwardly."
    ),
    ReleaseHistoryEntry(
        version = "v14.0",
        releaseDate = "Nov 8, 2026",
        headline = "Clear Standby vs. Offline Indicators",
        summary = "Gave reachable OFF switches a crisp dark-gray power ring and offline switches a distinct red Reconnect ring for one-tap recovery."
    ),
    ReleaseHistoryEntry(
        version = "v13.0",
        releaseDate = "Nov 8, 2026",
        headline = "Uniform Room Cards & Cleaner Rows",
        summary = "Aligned all collapsed room group cards to a consistent height and removed redundant room badges when grouped by room."
    ),
    ReleaseHistoryEntry(
        version = "v12.0",
        releaseDate = "Nov 8, 2026",
        headline = "Guided Empty-Home Onboarding & More Icons",
        summary = "Added a step-by-step Empty-Home welcome card and expanded automatic icon matching for holiday garlands, exhaust fans, sconces, and chandeliers."
    ),
    ReleaseHistoryEntry(
        version = "v11.0",
        releaseDate = "Nov 8, 2026",
        headline = "Multi-Gen Wi-Fi Setup & Auto 9-Port Manual IP",
        summary = "Added local setup support for both 1st-Gen OpenWrt and 2nd-Gen RTOS switches, cellular-safe Wi-Fi pinning, and automatic 9-port probing for manual IPs."
    ),
    ReleaseHistoryEntry(
        version = "v10.0",
        releaseDate = "Nov 7–8, 2026",
        headline = "Smart Appliance Illustrations",
        summary = "Automatically displays glowing holiday trees, spinning fans, dimmable lightbulbs, motion sensors, and wall switches based on each switch's name."
    ),
    ReleaseHistoryEntry(
        version = "v9.0",
        releaseDate = "Nov 7, 2026",
        headline = "Instant 3-State Startup Detection",
        summary = "Checks switches in parallel at startup so every row immediately reflects live ON, OFF Standby, or Disconnected hardware status."
    ),
    ReleaseHistoryEntry(
        version = "v8.0",
        releaseDate = "Nov 7, 2026",
        headline = "Zero-Cloud 10.22.22.1 Setup & Night Mode",
        summary = "Launched the local factory-reset switch setup wizard, live Auto-Off countdown badges, and one-tap dimmer Night Mode (20%)."
    ),
    ReleaseHistoryEntry(
        version = "v7.0",
        releaseDate = "Nov 7, 2026",
        headline = "Instant Wall-Switch Push Sync",
        summary = "Added sub-100ms local UPnP push updates whenever a physical wall switch is flipped, plus zero-Location-permission Wi-Fi subnet detection."
    ),
    ReleaseHistoryEntry(
        version = "v6.0",
        releaseDate = "Nov 7, 2026",
        headline = "Self-Healing UPnP Engine & Real LAN Mode",
        summary = "Added 2-stage port/IP self-healing, concurrency protection for 2nd-Gen RTOS 3-way switches, and a switch between Real LAN Mode and Demo Showroom Mode."
    ),
    ReleaseHistoryEntry(
        version = "v5.0",
        releaseDate = "Nov 7, 2026",
        headline = "Multi-Home Wi-Fi Profiles",
        summary = "Save and switch between multiple Wi-Fi locations like My Home or Cabin, each with its own remembered switches and one-tap Reconnect."
    ),
    ReleaseHistoryEntry(
        version = "v4.0",
        releaseDate = "Nov 7, 2026",
        headline = "Live Wi-Fi Signal & Response Badges",
        summary = "Added 4-bar wireless indicators and real-time latency badges (Excellent, Good, Slow, Offline) measured from live local commands."
    ),
    ReleaseHistoryEntry(
        version = "v3.0",
        releaseDate = "Nov 7, 2026",
        headline = "10-Foot Android TV Dashboard",
        summary = "Introduced full Android TV support with D-Pad remote navigation, room toggles, dimmer shortcuts, and one-click living room lighting scenes."
    ),
    ReleaseHistoryEntry(
        version = "v2.0",
        releaseDate = "Nov 7, 2026",
        headline = "Room Grouping & Master Room Controls",
        summary = "Group switches into collapsible room cards with room filter chips, a Master Room Power Ring, and a Master Room Dimmer Slider."
    ),
    ReleaseHistoryEntry(
        version = "v1.5",
        releaseDate = "Nov 7, 2026",
        headline = "Local Wi-Fi Switch Scanner (SSDP)",
        summary = "Added interactive local network discovery with one-tap switch adoption and pull-to-refresh on the Devices tab."
    ),
    ReleaseHistoryEntry(
        version = "v1.0",
        releaseDate = "Nov 7, 2026",
        headline = "Initial Launch of WeMi Light Control",
        summary = "First release with 100% local Wi-Fi switch and dimmer control (1%–100%), local schedules and auto-off timers, and a live local packet log."
    )
)
