# Multi-Home Wi-Fi Profiles & Switching

[← Back to User Guide Index](../../USER_GUIDE.md)

If you use smart switches from Belkin's discontinued platform across multiple locations — such as a **Main Home**, a **Lake Cabin**, and a **Studio Office** — **WeMi Light Control** keeps each location's switches, rooms, widgets, and IP subnets cleanly separated using **Wi-Fi Home Profiles**.

---

## 1. The Wi-Fi Home Selector Bar

At the top of the **Devices** tab, the **Wi-Fi Home Bar** displays:

- **Active Home Name & Single-Line Status Summary**:
  Shows the currently selected Home (for example, `My Home`) on a full-width top line, with a compact single-line summary below (`17/17 Active • 192.168.86.0/24`, or `16/17 Active • 1 Offline • 192.168.86.0/24`). On a fresh Chromebook before switches are discovered, it displays a clean `0/0 Active • Local LAN` summary instead of showing ChromeOS's virtual subnet.
- **One-Tap `Reconnect` Button**:
  Tap **`Reconnect`** on the right side of the card to immediately re-check every switch in the current Home and run a self-healing reconnection pass for any switches whose IP address or port changed.
- **Remembered Home Chips (`My Home (17)`, `Lake Cabin (2)`, `Add Home`)**:
  Tap any Home chip in the bottom row to switch to that Home's remembered switches and reconnect them, or tap **`Add Home`** to register a new location.

---

## 2. Privacy-First Network Detection & Smart Protections

WeMi detects your local Wi-Fi network without asking for intrusive Android GPS or Location permissions:

1. **Zero-Location-Permission Subnet Detection**:
   On Android phones, tablets, and Android TV, WeMi reads your local `/24` IPv4 subnet (for example, `192.168.86.0/24`) directly from Android's network connection info—no Location permission prompt required.
2. **Automatic Protection Against Factory Setup Hotspot Names**:
   Many Belkin switch firmwares (especially 2nd-Gen 3-Way switches `Wemo.LS3W.xxx` and 1st-Gen `WeMo.Switch.xxx` / `WeMo.Light.xxx`) mistakenly report their own factory setup hotspot name instead of your router's Wi-Fi name when queried. WeMi automatically filters out all `WeMo.*` and `Belkin.*` setup names so your **My Home** label is never overwritten by a switch hotspot name.
3. **Clean Chromebook (ChromeOS) Home Profiles**:
   On Chromebooks, Android runs inside a virtual `100.115.92.x` network. WeMi automatically ignores that virtual address so it never creates a bogus `"LAN 100.115.92.0/24"` Home—and as soon as your first switch is discovered or added, your Home profile automatically learns and saves your real router subnet (such as `192.168.86.0/24`).

---

## 3. Adding or Resetting Wi-Fi Home Profiles

### Adding a New Wi-Fi Home
1. Tap the **`Add Home`** chip at the end of the Wi-Fi Home Bar.
2. In the **Remember Wi-Fi Home Network** dialog, fill in:
   - **`Home Name`**: A friendly label such as `Beach House`, `Parents' Home`, or `Workshop`.
   - **`Wi-Fi Network Name / SSID`**: Optional Wi-Fi name label (for example, `Beach-WiFi-5G`).
   - **`Local IPv4 Subnet Prefix`**: Automatically pre-filled with your current home `/24` subnet on phones, tablets, and TVs (for example, `192.168.86` or `10.0.0`). On Chromebooks, this box intentionally starts blank so you can enter your real home router prefix (such as `192.168.1`) rather than ChromeOS's virtual subnet.
3. Tap **`Save Home Wi-Fi`**, then run an **SSDP Scan** to discover and adopt the switches at that location.

### Resetting Remembered Homes, Switches & Rooms
If you want to start fresh on your local network:
1. Open the **More** tab and tap **`Reset Homes, Discovered Switches & Rooms`**.
2. In the **`Reset Homes, Switches & Rooms?`** confirmation dialog:
   - Choose **`Reset & Rescan LAN`** to clear all saved homes, switches, and room assignments and immediately scan your Wi-Fi for switches from a clean **`My Home`** profile.
   - Choose **`Reset Empty`** to clear everything into an empty **`My Home`** without scanning right away.
   - Choose **`Cancel`** to keep your current setup.

*(Tip: If you have Home Screen widgets pinned before a reset and run **`Reset & Rescan LAN`**, your single-light and room widgets automatically reconnect to your rediscovered switches by hardware ID and room name!)*
