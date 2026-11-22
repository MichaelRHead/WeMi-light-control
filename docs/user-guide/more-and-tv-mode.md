# Master Controls, Diagnostics, Demo Mode & Android TV

[← Back to User Guide Index](../../USER_GUIDE.md)

The **More** tab and the **10-Foot Android TV Dashboard (`wemi TV`)** give you whole-home master switch actions, a Demo Mode for exploring without hardware, a live local packet inspector, open-source license notices, and full TV remote (D-Pad) control from your couch.

---

## 1. Master Switch Control (`More` Tab)

At the top of the **More** tab, the **Master Switch Control** card shows how many reachable switches are currently **ON** on your active Wi-Fi Home and gives you two whole-home buttons:
- **`All Switches ON`**: Turns every reachable switch and dimmer in your active home **ON** concurrently (paced safely for 2nd-Gen RTOS switches).
- **`All Switches OFF`**: Turns every reachable switch and dimmer **OFF** in one tap — handy at bedtime or when heading out.

---

## 2. Real LAN Mode vs. Demo Showroom Mode

Inside the **Offline Local UPnP / SOAP Engine** card on the **More** tab:

- **Real LAN Mode (Default on Physical Devices — `Demo Showroom Mode` OFF)**:
  - Starts clean on your local Wi-Fi and shows only real switches you discover via SSDP, set up via `10.22.22.1`, or add by IP.
  - If a switch is unplugged or unreachable, toggling it runs an automatic self-healing reconnection check and marks it **Offline** if it cannot be reached.
- **Demo Showroom Mode (`Demo Showroom Mode` ON)**:
  - Loads **14 sample switches, dimmers, fans, holiday trees, and motion sensors** across 3 sample Wi-Fi Homes (*Main Home*, *Lake Cabin*, *Studio Office*) so you can explore room grouping, dimming, widgets, rules, and TV mode without physical hardware nearby.
  - While Demo Showroom Mode is enabled, a **`Reset Preset`** button appears next to **`Scan LAN (SSDP)`** so you can restore the 14 sample devices anytime.

Other quick actions in this card:
- **`Scan LAN (SSDP)`**: Opens the live local network discovery scanner.
- **`Reset Homes, Discovered Switches & Rooms`**: Opens a confirmation prompt (**`Reset & Rescan LAN`**, **`Reset Empty`**, or **`Cancel`**) to wipe saved Wi-Fi Homes, switches, and room assignments back to a clean **`My Home`**.
- **`Provision New Switch AP (10.22.22.1)`**: Opens the zero-cloud setup wizard for brand-new or factory-reset switches.

---

## 3. Live UPnP / SOAP Packet Log Inspector

Below the engine card on the **More** tab, **UPnP / SOAP Packet Log** shows the **10 most recent** local commands and events on screen (out of the latest 50 kept in memory—including background Home Screen widget taps and Chromebook polling notices):
- Each entry displays:
  - **Device Name & Action** (for example, `Front Porch Light • SetBinaryState (ON)` or `Widget#Toggle`)
  - **Timestamp** (`h:mm:ss a`, such as `8:15:42 PM`)
  - **Target Local URL** (`POST http://192.168.1.104:49153/upnp/control/basicevent1`)
  - **Result & Response Speed** (`HTTP/1.1 200 OK • <BinaryState>1</BinaryState> (12ms)`)
- Tap **`Save/Share Log`** to export a full 5-section diagnostic report (`wemi-telemetry-latest.txt` combining your latest `10.22.22.1` setup trace, Wi-Fi Homes, device list, packet log, and Home Screen widgets—never including Wi-Fi passwords), copy it to your clipboard, and open Android's share sheet. See **[Troubleshooting, Screenshots & Sharing Telemetry Logs](telemetry-and-troubleshooting.md)** for details.
- Tap **`Clear`** to clear the log list.

---

## 4. Open Source License & Third-Party Notices (`More` Tab)

At the bottom of the **More** tab, the **Open Source License** card displays **`MIT License • Copyright (c) 2026 Michael R. Head & Contributors`**:
- Tap **`View License`** (or **`Hide`**) to expand:
  1. **Full MIT License Text**, matching the repository's [`LICENSE`](../../LICENSE) file.
  2. **Third-Party Open Source Notices**, listing the shipped permissive runtime libraries (**AndroidX, Jetpack Compose & Room** under `Apache License 2.0` and **Kotlin & Kotlinx Coroutines** under `Apache License 2.0`) and the Belkin non-affiliation trademark notice.

---

## 5. Android TV 10-Foot Dashboard (`wemi TV`)

When installed on an **Android TV** or **Google TV** device, WeMi automatically launches its dedicated 10-foot remote-friendly dashboard (**`wemi TV`**)—no mode switch required.

### Navigating `wemi TV` with Your TV Remote (D-Pad)

1. **Compact Single-Row Top Bar**:
   - Shows the **`wemi TV`** badge, active count (`X/Y ON`), a status pill for whichever switch is currently highlighted, a **`Dimmer X%` / `Done Dimming`** indicator whenever a dimmer is focused, your **Quick Scenes**, and **`Rescan`**.
   - Over 90% of the screen stays dedicated to your rooms and switch cards.
2. **3-Column Room-Grouped Switch Grid & Room Bars**:
   - Switches are grouped under full-width **Room Bars** (`LIVING ROOM`, `BEDROOM`, `KITCHEN`, etc., with `UNASSIGNED` last) in a 3-column grid.
   - Moving **Up** or **Down** between rooms from any column focuses the Room Bar while remembering which column you were in.
   - While a **Room Bar** is focused (**`FOCUSED • OK TOGGLE ROOM`** or **`FOCUSED • OK TOGGLE • L/R DIM`**):
     - Press **`OK / Select`** to toggle every switch in that room ON or OFF at once.
     - Press **`Left`** / **`Right`** (on rooms with reachable dimmers) to dim or brighten every dimmer in that room by **`-10%` / `+10%`**.
3. **Clear 10-Foot Cursor Focus (Including Empty-Home Start)**:
   - On launch, focus lands automatically on your first switch card—or directly on **`Rescan`** if your home has zero switches yet so a single **`OK`** press discovers your switches.
   - Focused switch cards stand out across the room with a bright **Electric Cyan** border, illuminated card surface, slight scale lift, and a **`FOCUSED • OK`** badge in the top-right corner.
4. **Google TV Remote-Friendly Inline Dimmer Slider**:
   - **Toggling Power**: On any switch or dimmer card, press **`OK / Select`** to toggle power ON or OFF (or reconnect if offline), and press **`Left` / `Right`** to move across the 3 columns.
   - **Adjusting Brightness (No Special Remote Keys Needed)**: On a reachable dimmer card, press **`Down`** once from the On/Off Toggle to focus the **Inline Dimmer Slider** right underneath it *(you can also press **`Play/Pause`**, **`Menu`**, **`Info`**, or long-press **`OK`**)*.
   - The card highlights in **Gold** with a **`DIM -/+`** badge in the corner, **`Done Dimming`** in the top bar, and the inline guide **`Left: -10%  •  Right: +10%   (Up: Toggle • Down: Next)`**:
     - Press **`Left`** / **`Right`** to step brightness down or up by **`10%`** (`10%–100%`).
     - Press **`Up`**, **`OK`**, **`Back`**, **`Play/Pause`**, or **`Menu`** to return to the On/Off Toggle.
     - Press **`Down`** to move smoothly to the switch or room below.
5. **One-Click TV Quick Scenes & Non-Blocking LAN Rescan**:
   - **`Movie 20%`**: Dims **all reachable smart dimmers/bulbs** on your active Wi-Fi Home to **`20%`** concurrently (leaving regular ON/OFF switches untouched).
   - **`Holiday ON`** *(shown automatically only when your active home includes matching holiday or festive switches—such as Christmas tree, holiday, Diwali/Dewali, Hanukkah, Halloween, or fairy/string lights)*: Turns matching holiday switches **ON** concurrently without touching everyday switches or holiday lights that are already ON.
   - **`All OFF`**: Turns every reachable switch and dimmer across all rooms **OFF** concurrently.
   - **`Rescan`**: Runs a background local Wi-Fi scan and refresh with a non-blocking status bar at the bottom of the screen—never popping up blocking dialogs over your TV.
