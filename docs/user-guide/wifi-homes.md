# Multi-Home Wi-Fi Profiles & Switching

[← Back to User Guide Index](../../USER_GUIDE.md)

If you have Belkin WeMo switches across multiple locations — such as a **Main Home**, a **Lake Cabin**, and a **Studio Office** — **WeMi Light Control** keeps each location's switches, rooms, and IP subnets cleanly separated using **Wi-Fi Home Profiles**.

---

## 1. The Wi-Fi Home Selector Bar

At the top of the **Devices** tab, the **Wi-Fi Home Bar** displays:

- **Active Home Name & Wi-Fi SSID Badge**:
  Shows the currently selected Home (e.g., `Main Home`) alongside a monospace badge showing the Wi-Fi SSID or subnet (`WeMi-Home-5G` or `LAN 192.168.1.0/24`).
- **Remembered Switch Status Summary**:
  Displays how many remembered switches in this Home are currently reachable (`5/5 Remembered Active • 192.168.1.0/24` or `4/5 Remembered Active • 1 Offline`).
- **1-Tap `Reconnect` Button**:
  Tap **`Reconnect`** on the right side of the card to immediately run a startup state probe (`GetBinaryState`) and a full SSDP / 9-port self-healing reconnection pass for every switch in the current Home.
- **Remembered Home Chips (`Main Home (5)`, `Lake Cabin (2)`, `Add Wi-Fi Home`)**:
  Tap any Home chip in the bottom row to switch your view to that Home's remembered switches and trigger a reconnect pass.

---

## 2. Zero-Permission Subnet Detection & Automatic SSID Sync

Unlike apps that require intrusive Android fine-location permissions (`ACCESS_FINE_LOCATION`) just to read your Wi-Fi network name:

1. **Zero-Permission `/24` Subnet Detection**:
   **WeMi Light Control** inspects your active Wi-Fi interface's IPv4 address (e.g., `192.168.1.50` → subnet `192.168.1`) using Android's `ConnectivityManager` without asking for GPS or Location permissions.
2. **Automatic Wi-Fi SSID Discovery from Hardware (`GetMetaInfo`)**:
   Once the app connects to any Belkin WeMo switch on your subnet, it reads the `AccessPointSSID` field reported by the switch's `metainfo1#GetMetaInfo` SOAP action and automatically labels your Wi-Fi Home with your real Wi-Fi network name!

---

## 3. Adding a New Wi-Fi Home Profile

To register an additional home or office network:

1. Tap the **`+ Add Wi-Fi Home`** chip at the end of the Wi-Fi Home Bar.
2. In the **Remember Wi-Fi Home Network** dialog, enter:
   - **Home Name**: A friendly label such as `Beach House`, `Parents' Home`, or `Workshop`.
   - **Wi-Fi Network Name / SSID**: Optional Wi-Fi SSID label (e.g., `Beach-WiFi-2.4G`).
   - **Local IPv4 Subnet Prefix**: Pre-filled with your currently detected `/24` subnet prefix (e.g., `192.168.1` or `10.0.0`).
3. Tap **`Save Home Wi-Fi`**.
4. Run an **`SSDP Scan`** to discover and adopt the switches at that location into your new Home profile.
