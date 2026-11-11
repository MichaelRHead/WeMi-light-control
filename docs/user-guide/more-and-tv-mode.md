# Master Controls, Diagnostics, Demo Mode & Android TV

[← Back to User Guide Index](../../USER_GUIDE.md)

The **More** tab and the **10-Foot Android TV Dashboard (`wemi TV`)** provide whole-home master switch actions, a live UPnP/SOAP packet inspector, a showcase Demo Mode, in-app open-source license and third-party notices, and native TV remote (D-Pad) support on Android TV devices.

---

## 1. Master Switch Control (`More` Tab)

At the top of the **More** tab, the **Master Switch Control** card shows how many reachable switches are currently **ON** across your home and provides two one-tap master buttons:
- **`All Switches ON`**: Turns every reachable switch and dimmer in your home **ON** concurrently (throttled safely for RTOS switches).
- **`All Switches OFF`**: Turns every reachable switch and dimmer in your home **OFF** with a single tap — ideal at bedtime or when leaving the house.

---

## 2. Real LAN Mode vs. Demo Showroom Mode

Inside the **Offline Local UPnP / SOAP Engine** card on the **More** tab:

- **Real LAN Mode (Default on Physical Hardware — `Demo Showroom Mode = OFF`)**:
  - Starts with a clean slate on your local `/24` Wi-Fi subnet and only displays real physical smart switches that you discover via SSDP, provision via `10.22.22.1`, or add by IP.
  - If a physical switch is unplugged or unreachable on Wi-Fi, toggling it performs a 2-stage self-healing reconnect attempt and accurately marks the device **`Offline`** if it cannot be reached.
- **Demo Showroom Mode (`Demo Showroom Mode = ON`)**:
  - Populates **14 showcase switches, dimmers, fans, Christmas tree lights, and motion sensors** across 3 sample Wi-Fi Homes (`Main Home`, `Lake Cabin`, `Studio Office`) so you can explore room grouping, dimming, rules, and the interface without physical hardware on your LAN.
  - Tapping **`Reset Preset`** restores the 14 classic showcase switches at any time.

Other quick settings in this card:
- **Header Logo Wordmark Switch**: Toggles the top header logo between **`wemi`** and classic **`wemo`**.
- **`Scan LAN (SSDP)`**: Opens the SSDP network discovery sheet.
- **`Reset Homes, Discovered Switches & Rooms`**: Clears all remembered Wi-Fi Homes, discovered switches, and room assignments in Real LAN Mode, resets your primary LAN profile back to a clean **`My Home`**, and triggers a fresh SSDP discovery scan.
- **`Provision New Switch AP (10.22.22.1)`**: Opens the zero-cloud Wi-Fi setup wizard.

---

## 3. Live UPnP / SOAP Packet Log Inspector

Near the bottom of the **More** tab, the **UPnP / SOAP Packet Log** card records the most recent HTTP SOAP commands and GENA events exchanged with your switches:
- Each log entry displays:
  - **Device Name & SOAP Action** (e.g., `Front Porch Light • SetBinaryState (ON)` or `SSDP M-SEARCH • urn:Belkin:service:basicevent:1`)
  - **Timestamp** (`HH:mm:ss.SSS`)
  - **Target UPnP Endpoint** (`POST http://192.168.1.104:49153/upnp/control/basicevent1`)
  - **Response Status & Round-Trip Latency** (`HTTP/1.1 200 OK • <BinaryState>1</BinaryState> (12ms)`)
- Tap **`Save/Share Log`** (`export_telemetry_file_button`) to save a complete telemetry report (`files/wemi-telemetry-latest.txt` combining your latest `10.22.22.1` setup snapshot, device inventory, and SOAP packet log), copy it to the clipboard, and open Android's share sheet (see **[Troubleshooting, Screenshots & Sharing Telemetry Logs](telemetry-and-troubleshooting.md)** for full details).
- Tap **`Clear`** to reset the packet log buffer.

---

## 4. Open Source License & Third-Party Notices (`More` Tab)

At the bottom of the **More** tab, the **Open Source License** card (`open_source_licenses_card`) summarizes **WeMi Light Control**'s open-source licensing (**`MIT License • Copyright (c) 2026 Michael R. Head & Contributors`**):
- Tap **`View License`** (`toggle_license_details_button`) to expand:
  1. **Full MIT License Text** (`mit_license_text_surface`), matching the root [`LICENSE`](../../LICENSE) file (`Copyright (c) 2026 Michael R. Head and WeMi Light Control Contributors`).
  2. **Third-Party Open Source Notices** (`third_party_notices_surface`), listing the shipped permissive runtime libraries (**AndroidX, Jetpack Compose & Room** under `Apache-2.0` and **Kotlin & Kotlinx Coroutines** under `Apache-2.0`) and the Belkin non-affiliation trademark notice.

---

## 5. Android TV 10-Foot Dashboard (`wemi TV`)

**WeMi Light Control** automatically launches in **Android TV Mode** when running on an Android TV / Google TV device (`UI_MODE_TYPE_TELEVISION`).

### Key Features of `wemi TV`:
1. **Full-Height 3-Column Room-Grouped Switch Grid**:
   - A single compact top bar (`wemi TV`, active switch count, focused switch status pill, quick scenes, and `Rescan`) dedicates over 90% of vertical screen height to your switches.
   - Switches are grouped under full-width **Room Section Headers** (`LIVING ROOM`, `BEDROOM`, `KITCHEN`, etc., with `Unassigned` last) inside a 3-column grid (`GridCells.Fixed(3)`). Pressing **`OK / Select`** on any Room Header toggles all switches in that room ON or OFF.
2. **Unmistakable 10-Foot Cursor Focus**:
   - The first switch card automatically receives D-Pad focus on launch.
   - The focused card stands out across the living room with a `3.5.dp` Electric Cyan border (`#22D3EE`), bright illuminated card surface (`#133B66`), subtle scale lift (`1.022x`), and a **`FOCUSED • OK`** badge in the top-right corner.
3. **Predictable Card Navigation & Non-Interfering Dimmer Adjust Mode**:
   - **Normal Navigation Mode**: **D-Pad `Up / Down / Left / Right`** always moves freely across the 3 columns of switch and dimmer cards without getting stuck on dimmers. Pressing **`OK / Select`** on any card (switch or dimmer) immediately toggles power **ON** or **OFF** (or triggers a 9-port reconnect if offline).
   - **Dimmer Adjust Mode**: To adjust a dimmer's brightness, press **`Play/Pause`**, **`Menu`**, or **Long-Press `OK`** on a focused dimmer card (or click its dimmer strip). The card border turns glowing Gold (`#FBBF24`) with a **`DIM -/+`** badge: use **`Left` / `Right`** to step brightness `-10%` / `+10%` (or **`Up` / `Down`** for `±20%`), then press **`OK`** or **`Back`** to finish and return to normal card navigation.
4. **1-Click TV Lighting Scenes & Non-Blocking LAN Rescan**:
   - **`Movie 20%`**: Dims Living Room lights to a soft `20%` glow and turns off bright overhead switches.
   - **`Holiday ON`**: Turns all holiday/tree switches **ON**.
   - **`All OFF`**: Turns every switch in the home **OFF**.
   - **`Rescan`**: Runs a background SSDP + state refresh with a bottom status toast—never popping up modal dialogs over the TV screen.

