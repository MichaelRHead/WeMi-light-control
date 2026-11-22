# Home Screen Light & Room Widgets

[← Back to User Guide Index](../../USER_GUIDE.md)

**WeMi Light Control** (listed as **WeMi Switch Control** in your Android launcher) includes fast, resizable **Home Screen Widgets** so you can turn an individual smart light, plug, or an entire room **ON** or **OFF** with a single tap—directly over your local Wi-Fi network without opening the app.

> [!NOTE]
> Home Screen Widgets are designed for **Android Phones and Tablets** (Android 7.0+ / API 24+, with one-tap pinning from inside the app on Android 8.0+ / API 26+). Motion sensors are read-only sensors and are not listed as toggle widgets.

---

## 1. Three Ways to Add a Widget

You can add as many single-light or whole-room widgets to your Android Home Screen as you like:

### Option A: Pin an Entire Room from the App (Fastest for Rooms)
1. Open the **Devices** tab in **Grouped by Room** view.
2. On any Room Group header card that has switches or dimmers, tap the **Add to Home Screen** icon (next to the collapse arrow).
3. Tap **Add** / **Add automatically** in your Android launcher's confirmation popup to place the room widget on your Home Screen.

### Option B: Pin a Single Light or Switch from the App (Fastest for One Device)
1. On the **Devices** tab, tap a reachable switch, dimmer, or plug row to expand its drawer.
2. Tap the **`Add Widget`** button at the bottom of the drawer.
3. Confirm in your Android launcher's popup to place the widget on your Home Screen.

*(If your launcher does not support one-tap pinning from inside apps, WeMi displays a short reminder showing how to add the widget using Option C below.)*

### Option C: Add from Your Android Home Screen Widget Drawer
1. **Long-press** an empty area of your Android Home Screen and choose **Widgets**.
2. Find **WeMi Switch Control** and drag **`Light or Room Toggle`** onto your Home Screen.
3. The **Choose Light or Room** screen opens automatically:
   - Choose the **`Rooms`** tab to control an entire room with one tap, or choose the **`Lights`** tab to control a single switch, dimmer, or smart plug.
   - If you have more than one saved **Wi-Fi Home** (for example, *My Home* and *Lake Cabin*), tap a Home chip near the top to filter the list to that location.
4. Tap any room or light to finish placing the widget.

---

## 2. Using Your Widget & Understanding Its 3 Visual States

Tap anywhere on a placed widget (or its right-side power ring) to toggle your light or room immediately over local Wi-Fi:

- **Single Light / Switch Widget**: Toggles that light or plug **ON** or **OFF**.
- **Room Widget**:
  - If **any** reachable switch in the room is currently **ON**, tapping the widget turns the **entire room OFF**.
  - If **all** switches in the room are **OFF**, tapping the widget turns the **entire room ON**.

Each widget mirrors the app's **three clear visual states** so you always know whether a light is ON, OFF in standby, or unreachable:

| Widget State | Appearance | Single Light Subtitle | Whole-Room Subtitle | What Tapping Does |
| :--- | :--- | :--- | :--- | :--- |
| **ON (Active)** | Ocean Teal / Cyan glow with lit power ring | `ON • <Room>` (or `ON • 75%` for dimmers) | `2/3 ON • Room` | Turns the light or room **OFF** |
| **OFF (Standby)** | Crisp white card with dark charcoal standby power ring | `OFF • <Room>` | `All OFF • 3 lights` | Turns the light or room **ON** |
| **Offline / Unreachable** | Soft red-tinted card with red retry-arrow button | `Offline • Tap to retry` | `Offline • Room` | Attempts automatic 9-port local reconnection and toggles once reachable |

---

## 3. Resizing, Reconfiguring & Automatic Sync

- **Resizable Layout**: Widgets default to a compact **3×1** tile and can be resized horizontally or vertically (down to **2×1** or wider for long switch names) by long-pressing the widget on your Home Screen and dragging its resize handles.
- **Reconfiguring a Widget**:
  - On Android launchers that support widget reconfiguration, long-press the widget and tap the **Settings / Pencil** icon to point it to a different light or room.
  - If you delete a switch from the app or reset your switches, any widget linked to that removed switch changes to **`Tap to reconfigure`**—just tap it to pick a new light or room.
- **Automatic Two-Way Sync**:
  - Tapping a widget updates both the widget and the WeMi app immediately.
  - Turning a switch ON/OFF inside the app or pressing the physical switch on your wall updates your Home Screen widgets automatically.
- **Smart Protection Built In**:
  - **Multi-Home Isolation**: Room widgets stay tied to the Wi-Fi Home where you created them, so a *Living Room* widget for *My Home* won't accidentally toggle lights at *Lake Cabin*.
  - **Reliable Identity Matching**: Light widgets track your switch's hardware identity (serial number / UPnP ID), so even if your router gives a switch a new IP address or you rescan your network, the widget keeps controlling the right switch.
  - **Rapid-Tap Safety**: Accidental double-taps are queued cleanly in order so commands don't collide on your switches.
- **Troubleshooting Widget Taps**:
  - Every widget toggle (including offline errors or timeouts) is automatically recorded in **More → UPnP / SOAP Packet Log** and included in **Save/Share Log** (`wemi-telemetry-latest.txt`). See [Troubleshooting, Screenshots & Sharing Telemetry Logs](telemetry-and-troubleshooting.md) if a widget ever fails to respond.
