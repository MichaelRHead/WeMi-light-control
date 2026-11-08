# Editing, Renaming, Reordering & Adding Switches

[← Back to User Guide Index](../../USER_GUIDE.md)

**WeMi Light Control** gives you full control over how your switches are named, ordered, grouped into rooms, and configured on your local network — including pushing name changes directly to the physical Belkin WeMo hardware.

---

## 1. Entering & Using Edit Mode

Tap **`Edit`** in the top-right corner of the green header bar to enter **Edit Mode** (the button label changes to **`Done`**).

While in **Edit Mode**, the right side of every switch row replaces its Power Ring with four management buttons:

| Icon | Action | Description |
| :--- | :--- | :--- |
| **`↑` (Up Arrow)** | **Move Up** | Moves the switch higher in the list (`sortOrder`), persisted in the local Room database. |
| **`↓` (Down Arrow)** | **Move Down** | Moves the switch lower in the list (`sortOrder`). |
| **`✎` (Pencil Icon)** | **Edit Device** | Opens the **Edit Device** dialog for that switch (you can also tap anywhere on the switch row while in Edit Mode). |
| **`🗑` (Trash Icon)** | **Delete Device** | Removes the switch from your saved list immediately. |

Tap **`Done`** in the top-right header (or press the Android **Back** button) to exit **Edit Mode** and return to normal power toggling.

> [!TIP]
> You can also open the **Edit Device** dialog without entering Edit Mode by tapping a switch row to expand its drawer and tapping the **`Configure IP`** chip at the bottom right.

---

## 2. Adding or Editing a Switch (`Add / Edit Device` Dialog)

Tap **`+`** in the top header to add a new switch, or edit an existing switch to configure:

1. **Quick Discovery & Setup Shortcuts (When Adding)**:
   - **`Scan Wi-Fi Network (SSDP)`**: Opens the live multicast/unicast scanner to discover switches automatically.
   - **`Provision New WeMo AP (10.22.22.1)`**: Opens the zero-cloud setup wizard for factory-reset switches.
2. **Device Name & Live Auto-Classified Icon Preview**:
   - Type a descriptive name such as `Christmas Tree`, `Bedroom Ceiling Fan`, `Porch Light`, or `Espresso Machine`.
   - Directly below the text field, the **Auto-Classified Icon Preview Card** updates **live as you type**, showing the exact custom illustration (`Tree Lights`, `Fan`, `Light`, `Motion`, or `Switch`) that will represent the device across the app.
3. **Sync Name to WeMo Hardware Toggle**:
   - Enabled by default (`ON`). When you save, **WeMi Light Control** sends a `urn:Belkin:service:basicevent:1#ChangeFriendlyName` SOAP request to the physical Belkin WeMo switch so its internal `<friendlyName>` is updated on the hardware itself (visible to all phones and TVs on your Wi-Fi).
4. **Room / Group Assignment**:
   - When adding a new switch, typing the device name automatically suggests a matching room (for example, typing `Kitchen Island Light` selects `Kitchen`).
   - Tap any preset room chip (**`Living Room`**, **`Bedroom`**, **`Kitchen`**, **`Hallway & Landing`**, **`Porch & Patio`**, **`Office`**, **`Bathroom`**, **`Basement`**) or type your own custom room name in the **Room Name** field.
5. **Hardware Type**:
   - Choose **`Light Switch / Plug`** (`SWITCH_PLUG`), **`Dimmer / Lightbulb`** (`LIGHTBULB_DIMMER`), or **`Motion Sensor`** (`MOTION_SENSOR`).
6. **IP Address & UPnP Port (With Automatic Hardware Probing)**:
   - Enter the switch's IPv4 address (e.g., `192.168.1.115`) and port (default `49153`).
   - **Automatic 9-Port Hardware Probe**: When you save a manually entered IP in Real LAN Mode, the app automatically probes all 9 Belkin WeMo ports (`49151–49159`) at that IP, fetches `/setup.xml`, `GetBinaryState`, `GetMetaInfo`, and `GetSignalStrength`, and populates the switch's real hardware Serial Number, MAC address, firmware version, and live `ON`/`OFF` state.
   - If you leave the **Device Name** blank when adding by IP, the app automatically adopts the switch's hardware `<friendlyName>` from `/setup.xml`.
7. **Remove Device from List**:
   - When editing an existing switch, tap **`Remove Device from List`** at the bottom of the dialog to delete it.
