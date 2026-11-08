# Room Grouping & Master Room Controls

[← Back to User Guide Index](../../USER_GUIDE.md)

**WeMi Light Control** organizes your switches by room so you can toggle an entire room's lights or adjust every dimmer in a room with a single tap.

---

## 1. The Room Filter & Grouping Bar

Directly above your switches on the **Devices** tab is a horizontally scrollable bar with two types of controls:

1. **Grouping Mode Toggle (`Grouped by Room` vs. `Flat List`)**:
   - **`Grouped by Room` (Default)**: Organizes switches under collapsible **Room Group Header Cards** with master room power and dimmer controls.
   - **`Flat List`**: Displays all switches in a single continuous list ordered by your custom sort order.
2. **Room Filter Chips (`All Rooms (N)`, `Living Room`, `Bedroom`, `Kitchen`, etc.)**:
   - Tap **`All Rooms`** to see every room in your active Wi-Fi Home.
   - Tap any specific room chip (e.g., **`Living Room`** or **`Porch & Patio`**) to filter the screen to just that room's switches. Tap the selected room chip again (or press the system **Back** button) to return to **All Rooms**.

---

## 2. Room Group Header Cards

When **Grouped by Room** is active, each room has an interactive **Room Group Header Card**:

| Element | What It Shows / Does |
| :--- | :--- |
| **Room Icon & Name** | Displays a room-specific icon (Sofa for Living Room, Bed for Bedroom, Kitchen for Kitchen, Deck for Porch/Patio, etc.) and the room title. |
| **Active Count Pill (`X/Y ON`)** | Shows how many controllable switches in the room are currently **ON** out of the total reachable switches in that room. |
| **Room Telemetry Subtitle** | Shows reachable device count, average SOAP round-trip latency across the room (`Avg 12ms`), and average dimmer brightness (`75% dim`). |
| **Room Master Dimmer Button (`Tune` Sliders Icon)** | Appears automatically if the room contains one or more dimmable switches/bulbs. Tap to open the **Room Master Brightness Drawer**. |
| **Collapse / Expand Chevron (`^` / `v`)** | Tap the chevron (or anywhere on the header card background) to collapse or expand the individual switch rows inside that room. |
| **Master Room Power Ring (Right Side)** | Tap to **toggle the entire room ON or OFF at once**:<br>• If **any** switch in the room is currently `ON`, tapping the Master Room Power Ring turns **all** switches in that room `OFF`.<br>• If **all** switches in the room are currently `OFF`, tapping the ring turns **all** switches in that room `ON`. |

---

## 3. Room Master Brightness Drawer

When a room contains dimmable switches (`F7C059` / `WDS060` Smart Dimmers), tapping the **Tune** icon on the Room Group Header Card opens the **Room Master Brightness Drawer**:

- **Master Brightness Slider (`1%–100%`)**: Dragging and releasing the slider sets every dimmable switch in that room to the same brightness level in parallel (protected by RTOS-safe concurrency limiting).
- **Quick Room Presets**: Tap **`25%`**, **`50%`**, **`75%`**, or **`100%`** to set the entire room's lighting level with a single tap.

---

## 4. How Switches Are Assigned to Rooms

1. **Automatic Room Inference**:
   When a switch is discovered via SSDP or added by name, **WeMi Light Control** automatically infers its initial room from keywords in its name:
   - `Bedroom`, `Bed`, `Nursery`, `Guest` → **`Bedroom`**
   - `Kitchen`, `Dining`, `Pantry`, `Island`, `Coffee` → **`Kitchen`**
   - `Hall`, `Hallway`, `Stair`, `Foyer`, `Entry`, `Landing`, `Corridor` → **`Hallway & Landing`**
   - `Porch`, `Patio`, `Deck`, `Garage`, `Yard`, `Garden`, `Outdoor`, `Exterior`, `Driveway` → **`Porch & Patio`**
   - `Office`, `Desk`, `Study`, `Studio`, `Den`, `Library` → **`Office`**
   - `Bath`, `Bathroom`, `Vanity`, `Restroom`, `Powder` → **`Bathroom`**
   - `Basement`, `Cellar`, `Rec Room`, `Utility`, `Laundry` → **`Basement`**
   - All other names default to **`Living Room`**.
2. **Customizing a Switch's Room**:
   You can move any switch to a preset room or type any custom room name (e.g., `Kids' Playroom`, `Workshop`, `Sunroom`) in the **Add / Edit Device** dialog (see [Editing, Renaming, Reordering & Adding Switches](editing-and-managing-devices.md)). Once saved, your custom room assignment is preserved across future SSDP network scans.
