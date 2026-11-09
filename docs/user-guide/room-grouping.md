# Room Grouping & Master Room Controls

[← Back to User Guide Index](../../USER_GUIDE.md) • [Discovery & Room Grouping Architecture (`DISCOVERY_AND_ROOM_GROUPING.md`)](../../DISCOVERY_AND_ROOM_GROUPING.md)

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

1. **Automatic Room Inference (`normalizeDeviceNameForInference` & `inferRoomFromName`)**:
   When a switch is discovered via SSDP or added by name, **WeMi Light Control** normalizes CamelCase, underscores, and hyphens (`"StairwayLight"` → `"stairway light"`, `"MasterBathVanity"` → `"master bath vanity"`) and infers its initial room in specificity order (so compound names like `"Master Bath Vanity"` or `"Guest Bathroom Light"` resolve to **`Ensuite`** before broad modifiers like `"Master"` or `"Guest"`):
   - `Kitchen`, `Pantry`, `Island`, `Breakfast`, `Nook`, `Under-Cabinet`, `Countertop` → **`Kitchen`**
   - `Dining`, `Buffet`, `Hutch`, `China Cabinet` → **`Dining Room`**
   - `Ensuite`, `En-Suite`, `Bath`, `Bathroom`, `Powder`, `Vanity`, `Restroom`, `Lavatory`, `Shower`, `WC` → **`Ensuite`**
   - `Hall`, `Hallway`, `Landing`, `Stair` (`Stairs`, `Stairway`, `Staircase`, `Upstairs`, `Downstairs`), `Foyer`, `Entry`, `Vestibule`, `Mudroom`, `Corridor`, `Breezeway` → **`Hallway & Landing`**
   - `Bedroom`, `Bed`, `Bedside`, `Nightstand`, `Headboard`, `Dresser`, `Wardrobe`, `Crib`, `Loft`, `Master`, `Primary`, `Nursery`, `Guest`, `Bunk`, `Dorm`, `Girls`/`Girl`, `Boys`/`Boy`, `Kids`/`Kid`, `Children` → **`Bedroom`**
   - `Patio`, `Porch`, `Veranda`, `Lanai`, `Outdoor`, `Outside`, `Exterior`, `Garden`, `Dock`, `Pier`, `Yard`, `Deck`, `Flood`, `Walkway`, `Sidewalk`, `Pathway`, `Balcony`, `Terrace`, `Gazebo`, `Pergola`, `Pool`, `Fountain`, `Landscape` → **`Outdoor`**
   - `Garage`, `Workshop`, `Workbench`, `Driveway`, `Carport`, `Shed`, `Barn` → **`Garage`**
   - `Basement`, `Cellar`, `Laundry`, `Utility`, `Boiler`, `Furnace`, `Sump`, `Rec Room`, `Game Room`, `Media Room`, `Theater` → **`Basement`**
   - `Office`, `Study`, `Desk`, `Workstation`, `Studio`, `Library` → **`Office`**
   - `Living`, `Family`, `Den`, `Lounge`, `Great Room`, `Parlor`, `Sitting`, `Sunroom`, `Solarium`, `Mantel`, `Hearth`, `Fireplace`, `Couch`, `Sofa` → **`Living Room`**
   - All other unrecognized names default to **`Unassigned`** (displayed at the bottom of your room groups) instead of crowding **`Living Room`**.
2. **Automatic Migration of Switches Previously Stuck in `Living Room` (`resolveDeviceRoomAssignment`)**:
   On app startup, during SSDP network scans, and on home reconnection in Real LAN Mode, any switch previously saved in **`Living Room`** whose name does not match a Living Room keyword (for example, `Shprese Bed Lamp`, `Stairway Light`, `Girls Light`, `Mike Bed Light`) is automatically moved to its inferred room (`Bedroom`, `Hallway & Landing`, `Ensuite`, or `Unassigned`).
3. **Customizing a Switch's Room**:
   You can move any switch to a preset room or type any custom room name (e.g., `Kids' Playroom`, `Workshop`, `Sunroom`) in the **Add / Edit Device** dialog (see [Editing, Renaming, Reordering & Adding Switches](editing-and-managing-devices.md)). Once saved to a custom room, your assignment is preserved across future SSDP network scans.
