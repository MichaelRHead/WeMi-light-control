# Multi-Home Wi-Fi Profiles & Switching

[← Back to User Guide Index](../../USER_GUIDE.md)

If you have Belkin WeMo switches across multiple locations — such as a **Main Home**, a **Lake Cabin**, and a **Studio Office** — **WeMi Light Control** keeps each location's switches, rooms, and IP subnets cleanly separated using **Wi-Fi Home Profiles**.

---

## 1. The Wi-Fi Home Selector Bar

At the top of the **Devices** tab, the **Wi-Fi Home Bar** displays:

- **Active Home Name & Single-Line Status Summary**:
  Shows the currently selected Home (e.g., `My Home`) on a full-width reactive top line, with a compact single-line status summary below (`17/17 Active • 192.168.86.0/24` or `16/17 Active • 1 Offline • 192.168.86.0/24`).
- **1-Tap `Reconnect` Button**:
  Tap **`Reconnect`** on the right side of the card to immediately run a startup state probe (`GetBinaryState`) and a full SSDP / 9-port self-healing reconnection pass for every switch in the current Home.
- **Remembered Home Chips (`My Home (17)`, `Lake Cabin (2)`, `Add Home`, `Reset`)**:
  Tap any Home chip in the bottom row to switch your view to that Home's remembered switches and trigger a reconnect pass, tap **`Add Home`** to register a new location, or tap **`Reset`** to clear remembered homes/switches/rooms and rescan your LAN.

---

## 2. Zero-Permission Subnet Detection & Smart SSID Filtering

Unlike apps that require intrusive Android fine-location permissions (`ACCESS_FINE_LOCATION`) just to read your Wi-Fi network name:

1. **Zero-Permission `/24` Subnet Detection**:
   **WeMi Light Control** inspects your active Wi-Fi interface's IPv4 address (e.g., `192.168.86.50` → subnet `192.168.86`) using Android's `ConnectivityManager` without asking for GPS or Location permissions.
2. **Smart Wi-Fi SSID Sync & Belkin Soft-AP Filtering (`GetMetaInfo`)**:
   When the app queries a Belkin WeMo switch's `metainfo1#GetMetaInfo` SOAP action, it inspects the reported `AccessPointSSID` field. Because many Belkin firmwares (such as 2nd-Gen 3-Way switches `Wemo.LS3W.xxx` and 1st-Gen `WeMo.Switch.xxx` / `WeMo.Light.xxx`) return the switch's own factory setup hotspot name in that field, **WeMi Light Control** automatically filters out all `WeMo.*` / `Belkin.*` soft-AP SSIDs and never overwrites your Home Name (`My Home`) with a device SSID.

---

## 3. Adding or Resetting Wi-Fi Home Profiles

To register an additional home or office network:

1. Tap the **`Add Home`** chip at the end of the Wi-Fi Home Bar.
2. In the **Remember Wi-Fi Home Network** dialog, enter:
   - **Home Name**: A friendly label such as `Beach House`, `Parents' Home`, or `Workshop`.
   - **Wi-Fi Network Name / SSID**: Optional Wi-Fi SSID label (e.g., `Beach-WiFi-2.4G`).
   - **Local IPv4 Subnet Prefix**: Pre-filled with your currently detected `/24` subnet prefix (e.g., `192.168.86` or `10.0.0`).
3. Tap **`Save Home Wi-Fi`**, then run an **`SSDP Scan`** to discover and adopt the switches at that location.

### Resetting Remembered Homes, Switches & Rooms
If you want to start fresh on your local network:
1. Tap the **`Reset`** chip in the **Wi-Fi Home Bar** (or tap **`Reset Homes, Discovered Switches & Rooms`** on the **More** tab).
2. Choose **`Reset & Rescan LAN`** to clear all saved homes, switches, and room assignments and immediately run a fresh SSDP discovery scan on your current `/24` subnet, or choose **`Reset Empty`** to reset to a clean empty **`My Home`** profile.
