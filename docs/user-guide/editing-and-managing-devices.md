# Editing, Renaming, Reordering & Adding Switches

[← Back to User Guide Index](../../USER_GUIDE.md)

**WeMi Light Control** lets you rename switches, push new names directly to the physical switch hardware, organize rooms, reorder your list, or add a switch manually by IP address.

---

## 1. Using Edit Mode

Tap **`Edit`** in the top-right corner of the **Ocean Teal & Deep Sapphire** top header bar to enter **Edit Mode** (if you are on the **Rules** or **More** tab, WeMi switches to **Devices** automatically, and the button changes to **`Done`**).

While in **Edit Mode**, the right side of every switch row replaces its Power Ring with four management buttons:

| Icon | Action | What It Does |
| :--- | :--- | :--- |
| **`↑` (Up Arrow)** | **Move Up** | Moves the switch higher in your list. In **Grouped by Room** or single-room filter view, it swaps with the switch above it in the **same room** without disturbing other rooms. In **Flat List** view, it reorders across your active Wi-Fi Home. |
| **`↓` (Down Arrow)** | **Move Down** | Moves the switch lower within the same room (when grouped/filtered) or across your active Wi-Fi Home (in Flat List view). |
| **`✎` (Pencil Icon)** | **Edit Device** | Opens the **Edit** dialog for that switch (you can also tap anywhere on the row while in Edit Mode). |
| **`🗑` (Trash Icon)** | **Delete Device** | Removes the switch from your saved list, clears its cached on-device rules snapshot, and stops its live push subscription immediately. |

Tap **`Done`** in the top-right header (or press your device's **Back** button) to exit **Edit Mode** and return to normal switching.

> [!TIP]
> For any reachable switch or dimmer, you can also open the **Edit** dialog without entering Edit Mode: tap the switch row to expand its drawer and tap **`Configure IP`**. For offline switches or motion sensors, enter **Edit Mode** first.

---

## 2. Adding or Editing a Switch

Tap **`+`** in the top header (on the **Devices** or **More** tab) to open **Add Smart Switch Device**, or edit an existing switch to open **Edit `<Device Name>`**:

1. **Quick Discovery & Setup Shortcuts (When Adding a New Switch)**:
   - **`Scan Local Wi-Fi (SSDP UPnP)`**: Opens the live network scanner to find switches automatically.
   - **`Provision New Switch AP (10.22.22.1)`**: Opens the zero-cloud setup wizard for brand-new or factory-reset switches.
2. **`Friendly Name` & Live Auto-Classified Icon Preview**:
   - Type a descriptive name such as `Christmas Tree`, `Bedroom Ceiling Fan`, `Porch Light`, or `Espresso Machine`.
   - Directly below the text box, the **Auto-Classified Icon** preview updates **live as you type**, showing whether the switch will display as **`Tree Lights`**, **`Fan`**, **`Light`**, **`Motion`**, or **`Switch`**.
   - **Tip for Manual IP Adds**: If you leave **Friendly Name** blank when adding a new switch by IP address, WeMi automatically reads and adopts the real name stored inside the physical switch!
3. **`Sync Name to Switch Hardware` Toggle**:
   - Enabled by default (**ON**). When you tap **Save**, WeMi sends a local rename command (`ChangeFriendlyName`) directly to the physical switch so the new name is stored inside the hardware itself and appears consistently on other phones, tablets, Chromebooks, and TVs on your Wi-Fi.
4. **`Room / Group` Assignment**:
   - When adding a new switch, typing its name automatically suggests a matching room (defaulting to **`Unassigned`** if no room keyword matches).
   - Tap any of the **11 Room Preset Chips** (**`Living Room`**, **`Bedroom`**, **`Kitchen`**, **`Dining Room`**, **`Hallway & Landing`**, **`Ensuite`**, **`Outdoor`**, **`Garage`**, **`Basement`**, **`Office`**, **`Unassigned`**) or type any custom room name in **`Room Name (or type a custom room)`**.
5. **`Hardware Type`**:
   - Choose **`Light Switch / Plug`**, **`Dimmer / Lightbulb`**, or **`Motion Sensor`**.
6. **`Local IP Address` & `Port` (Automatic 9-Port Detection)**:
   - Enter the switch's private local IPv4 address (`10.x.x.x`, `172.16–31.x.x`, or `192.168.x.x`, such as `192.168.1.115`) and TCP port (`1–65535`, default `49153`). WeMi validates the IP and port inline so public or invalid addresses cannot be saved accidentally.
   - When you save a manually entered IP in **Real LAN Mode**, WeMi automatically checks all 9 smart-switch UPnP ports (`49151–49159`) at that IP and fills in the switch's real hardware type, serial number, firmware version, and live ON/OFF state.
7. **`Initial Brightness` (Dimmers Only)**:
   - When **`Dimmer / Lightbulb`** is selected, you can set its starting brightness (`1%–100%`) right inside the dialog.
8. **`Remove Device from List` (When Editing)**:
   - Scroll to the bottom of the **Edit** dialog and tap **`Remove Device from List`** to delete the switch.
