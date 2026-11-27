# Room Grouping & Master Room Controls

[← Back to User Guide Index](../../USER_GUIDE.md) • [Discovery & Room Grouping Architecture (`DISCOVERY_AND_ROOM_GROUPING.md`)](../../DISCOVERY_AND_ROOM_GROUPING.md)

**WeMi Light Control** automatically groups your switches by room so you can collapse rooms you aren't using, toggle every light in a room with one tap, dim an entire room together, or pin a whole-room widget to your Android Home Screen.

---

## 1. The Room Filter & Grouping Bar

Just above your switches on the **Devices** tab is a horizontally scrollable bar with two controls:

1. **View Toggle (`Grouped by Room` vs. `Flat List`)**:
   - **`Grouped by Room` (Default)**: Organizes your switches under collapsible **Room Group Header Cards** with whole-room power and dimmer controls.
   - **`Flat List`**: Shows all switches in a single continuous list in your custom sort order.
2. **Room Filter Chips (`All Rooms (N)`, `Living Room`, `Bedroom`, `Kitchen`, `Outdoor`, etc.)**:
   - Tap **`All Rooms`** to show every room in your active Wi-Fi Home.
   - Tap any room chip to focus the screen on just that room. Tap the selected room chip again (or press **Back**) to return to **All Rooms**.

---

## 2. Room Group Header Cards

In **Grouped by Room** view, each room card gives you instant overview and whole-room controls:

| Element | What It Shows / Does |
| :--- | :--- |
| **Room Icon & Name** | Shows a matching room icon (chair for Living Room, bed for Bedroom, bathtub for Ensuite/Bath, kitchen icon for Kitchen/Dining, deck for Outdoor/Patio/Porch, or room icon for other rooms) and the room title on a single unobstructed line. |
| **Active Count Pill (`X/Y ON`)** | Shows how many reachable switches in that room are currently **ON**. |
| **Room Telemetry Subtitle** | Displays reachable switch count, average local response time (`Avg 12ms`), and average dimmer brightness (`75% dim`) when the room contains dimmers. |
| **Room Master Dimmer (`Tune` Sliders Icon)** | Appears automatically whenever the room has one or more reachable dimmers. Tap to open the **Room Master Brightness** slider. |
| **Pin Room Widget (`Add to Home Screen` Icon)** | Appears on rooms with controllable switches. Tap to pin a one-tap whole-room toggle widget to your Android Home Screen (see [Home Screen Light & Room Widgets](home-screen-widgets.md)). |
| **Collapse / Expand Arrow (`^` / `v`)** | Tap the arrow—or anywhere on the room header background—to hide or show the individual switches inside that room. |
| **Master Room Power Ring (Right Side)** | Tap to **toggle every reachable switch in the room at once**:<br>• If **any** switch in the room is **ON**, tapping turns **all** switches in that room **OFF**.<br>• If **all** switches in the room are **OFF**, tapping turns **all** switches in that room **ON**. |

---

## 3. Room Master Brightness Slider

When a room contains dimmable switches (`F7C059` / `WDS060`), tapping the **Tune** sliders icon on the Room Group header expands the **Room Master Brightness** drawer:

- **Continuous Master Brightness Slider (`1%–100%`)**: Drag and release the slider to set every reachable dimmer in that room to the same brightness level together, while leaving regular ON/OFF switches in the room untouched.
- **Android TV (`wemi TV`) Room Bars**: On Android TV, each room renders a full-width **Room Bar** above its 3-column switch cards (with **`Unassigned`** placed last). Navigating **Up** or **Down** between rooms from any column focuses the Room Bar (**`FOCUSED • OK TOGGLE ROOM`** or **`FOCUSED • OK TOGGLE • L/R DIM`**) and immediately updates the top-bar OK hint (`<Room Name> • OK Toggle Room` or `• OK Toggle Room • ◄/► Dim`), where pressing **`OK`** toggles the entire room ON or OFF, pressing **`Left`** / **`Right`** dims or brightens every reachable dimmer in that room in **`10%`** steps, and case-insensitive room merges keep remote focus locked smoothly in place.

---

## 4. How Switches Are Automatically Grouped into Rooms

Belkin switches store a friendly name on the switch itself, but they don't store a room tag—so WeMi automatically figures out the right room from each switch's name:

1. **Smart Name Recognition**:
   When a switch is discovered or added, WeMi splits joined words (`"StairwayLight"`, `"MasterBathVanity"`, `"WCLight"`), hyphens, and underscores, and checks specific room clues first so compound names like *"Master Bath Vanity"*, *"Upstairs Bedroom"*, or *"Finished Basement"* land in the right room:
   - **`Kitchen`**: `Kitchen`, `Pantry`, `Island`, `Breakfast`, `Nook`, `Under-Cabinet`, `Countertop`
   - **`Dining Room`**: `Dining`, `Buffet`, `Hutch`, `China Cabinet`
   - **`Ensuite`**: `Ensuite`, `En-Suite`, `Bath`, `Bathroom`, `Powder`, `Vanity`, `Restroom`, `Lavatory`, `Shower`, `WC`
   - **`Hallway & Landing`**: `Hall`, `Hallway`, `Landing`, `Stair` (`Stairs`, `Stairway`, `Staircase`, `Stairwell`), `Foyer`, `Entry`, `Vestibule`, `Mudroom`, `Corridor`, `Breezeway`
   - **`Bedroom`**: `Bedroom`, `Bed`, `Bedside`, `Nightstand`, `Headboard`, `Dresser`, `Wardrobe`, `Crib`, `Cradle`, `Loft`, `Master`, `Primary`, `Nursery`, `Guest`, `Bunk`, `Dorm`, `Girls`/`Girl`, `Boys`/`Boy`, `Kids`/`Kid`, `Children`
   - **`Outdoor`**: `Patio`, `Porch`, `Veranda`, `Lanai`, `Outdoor`, `Outside`, `Exterior`, `Garden`, `Dock`, `Pier`, `Yard`, `Deck`, `Flood`, `Walkway`, `Sidewalk`, `Pathway`, `Balcony`, `Terrace`, `Gazebo`, `Pergola`, `Trellis`, `Pool`, `Fountain`, `Landscape`
   - **`Garage`**: `Garage`, `Workshop`, `Workbench`, `Driveway`, `Carport`, `Shed` / `Toolshed` / `Woodshed`, `Barn`
   - **`Basement`**: `Basement`, `Cellar`, `Laundry`, `Utility`, `Boiler`, `Furnace`, `Sump`, `Crawlspace`, `Rec Room`, `Game Room`, `Media Room`, `Theater`
   - **`Office`**: `Office`, `Study`, `Desk`, `Workstation`, `Studio`, `Library`
   - **`Living Room`**: `Living`, `Family`, `Den`, `Lounge`, `Great Room`, `Parlor`, `Sitting`, `Sunroom`, `Solarium`, `Mantel`, `Hearth`, `Fireplace`, `Couch`, `Sofa`
   - **`Hallway & Landing`** *(floor fallback)*: `Upstairs` or `Downstairs` when no more specific room word appears (such as *"Upstairs Light"*)
   - **`Unassigned`**: Switches whose names don't mention a room are placed cleanly in **`Unassigned`** at the bottom of your room list instead of cluttering **Living Room**.
2. **Unified Case-Insensitive Rooms & Legacy Cleanup**:
   - Capitalization differences merge automatically (for example, `"Kitchen"` and `"kitchen"` appear together under one **`Kitchen`** header across Phones, Tablets, Chromebooks, Android TV, and Home Screen Widgets).
   - If an older version of the app previously placed a non-living-room switch (like *"Stairway Light"* or *"Girls Lamp"*) into **Living Room**, WeMi automatically sorts it into its matching room on startup or scan in Real LAN Mode.
3. **Customizing Rooms, Reordering & Pinning Room Widgets**:
   - You can assign any switch to one of the 11 preset rooms or type any custom room name (such as *Playroom* or *Sunroom*) in **Edit Device**. Once you choose a custom room, future network scans respect your choice.
   - Use **Edit Mode** (`↑` / `↓`) while **Grouped by Room** is active to reorder switches inside their room without affecting other rooms (see [Editing, Renaming, Reordering & Adding Switches](editing-and-managing-devices.md)).
   - Tap the **Add to Home Screen** icon on any room header to pin a whole-room toggle widget directly to your Android Home Screen (see [Home Screen Light & Room Widgets](home-screen-widgets.md)).
