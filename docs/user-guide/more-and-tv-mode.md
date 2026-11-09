# Master Controls, Diagnostics, Demo Mode & Android TV

[← Back to User Guide Index](../../USER_GUIDE.md)

The **More** tab and the **10-Foot Android TV Dashboard (`wemi TV`)** provide whole-home master switch actions, a live UPnP/SOAP packet inspector, a showcase Demo Mode, and full TV remote (D-Pad) support.

---

## 1. Master Switch Control (`More` Tab)

At the top of the **More** tab, the **Master Switch Control** card shows how many reachable switches are currently **ON** across your home and provides two one-tap master buttons:
- **`All Switches ON`**: Turns every reachable switch and dimmer in your home **ON** concurrently (throttled safely for RTOS switches).
- **`All Switches OFF`**: Turns every reachable switch and dimmer in your home **OFF** with a single tap — ideal at bedtime or when leaving the house.

---

## 2. Real LAN Mode vs. Demo Showroom Mode

Inside the **Offline Local UPnP / SOAP Engine** card on the **More** tab:

- **Real LAN Mode (Default on Physical Hardware — `Demo Showroom Mode = OFF`)**:
  - Starts with a clean slate on your local `/24` Wi-Fi subnet and only displays real physical Belkin WeMo switches that you discover via SSDP, provision via `10.22.22.1`, or add by IP.
  - If a physical switch is unplugged or unreachable on Wi-Fi, toggling it performs a 2-stage self-healing reconnect attempt and accurately marks the device **`Offline`** if it cannot be reached.
- **Demo Showroom Mode (`Demo Showroom Mode = ON`)**:
  - Populates **13 showcase switches, dimmers, fans, Christmas tree lights, and motion sensors** across 3 sample Wi-Fi Homes (`Main Home`, `Lake Cabin`, `Studio Office`) so you can explore room grouping, dimming, rules, and the Android TV interface without physical WeMo hardware on your LAN.
  - Tapping **`Reset Preset`** restores the 13 classic showcase switches at any time.

Other quick settings in this card:
- **Header Logo Wordmark Switch**: Toggles the top header logo between **`wemi`** and classic **`wemo`**.
- **`Scan LAN (SSDP)`**: Opens the SSDP network discovery sheet.
- **`Reset Homes, Discovered Switches & Rooms`**: Clears all remembered Wi-Fi Homes, discovered switches, and room assignments in Real LAN Mode, resets your primary LAN profile back to a clean **`My Home`**, and triggers a fresh SSDP discovery scan.
- **`Provision New WeMo AP (10.22.22.1)`**: Opens the zero-cloud Wi-Fi setup wizard.

---

## 3. Live UPnP / SOAP Packet Log Inspector

At the bottom of the **More** tab, the **UPnP / SOAP Packet Log** card records the most recent HTTP SOAP commands and GENA events exchanged with your switches:
- Each log entry displays:
  - **Device Name & SOAP Action** (e.g., `Front Porch Light • SetBinaryState (ON)` or `SSDP M-SEARCH • urn:Belkin:service:basicevent:1`)
  - **Timestamp** (`HH:mm:ss.SSS`)
  - **Target UPnP Endpoint** (`POST http://192.168.1.104:49153/upnp/control/basicevent1`)
  - **Response Status & Round-Trip Latency** (`HTTP/1.1 200 OK • <BinaryState>1</BinaryState> (12ms)`)
- Tap **`Save/Share Log`** (`export_telemetry_file_button`) to save a complete telemetry report (`files/wemi-telemetry-latest.txt` combining your latest `10.22.22.1` setup snapshot, device inventory, and SOAP packet log), copy it to the clipboard, and open Android's share sheet.
- Tap **`Clear`** to reset the packet log buffer.

---

## 4. Android TV 10-Foot Dashboard (`wemi TV`)

**WeMi Light Control** automatically launches in **Android TV Mode** when running on an Android TV / Google TV device (`UI_MODE_TYPE_TELEVISION`), and can also be opened on any phone or tablet by tapping the **`TV`** button in the top header bar.

### Key Features of `wemi TV`:
1. **10-Foot High-Contrast Dark UI**:
   - Designed for viewing across the living room with glowing cyan D-Pad focus rings (`#22D3EE`) and large hardware/category illustrations (`Tree Lights`, `Fan`, `Light`, `Motion`, `Switch`).
2. **Hardware TV Remote (D-Pad) Shortcuts**:
   - **D-Pad Arrows (`Up` / `Down` / `Left` / `Right`)**: Navigate between switch cards, room filters, and scene buttons.
   - **D-Pad Center / `Enter`**: Toggle the focused switch **ON** or **OFF** (or trigger a reconnect if offline).
   - **Media `Play / Pause` Button**: Instantly toggles **all switches in the currently selected/focused room ON or OFF**!
   - **`-15%` / `+15%` Dimmer Buttons**: On dimmable light cards, step brightness down or up in 15% increments directly from the remote.
3. **1-Click TV Lighting Scenes**:
   - **`Movie Night`**: Dims Living Room lights to a soft `20%` glow and turns off bright overhead switches.
   - **`Bright Room`**: Turns all switches in the current room **ON** at `100%` brightness.
   - **`Nightlight`**: Sets dimmable lights to `20%` and turns non-dimmable switches **OFF**.
   - **`All Off`**: Turns every switch in the home **OFF**.
4. **Interactive On-Screen Virtual Remote Pad**:
   - Toggle the **`Remote Pad`** button in the top-right corner of the TV dashboard to show or hide an on-screen D-Pad (`▲`, `▼`, `◀`, `▶`, `OK`, `Room I/O`, `-15%`, `+15%`) for touchscreens or air-mouse remotes.
