# On-Device Switch Rules, Timers & Schedules

[← Back to User Guide Index](../../USER_GUIDE.md)

The **Rules** tab manages **On-Device Switch Rules** (**`On-Switch`**) stored directly inside each compatible switch's own flash memory—so your schedules, auto-off delays, and wall-paddle long-press actions keep running reliably over your home Wi-Fi **even when your phone is turned off or away from home**, with no Belkin cloud account or external server required.

---

## 1. Supported Hardware & Capability Badges

Classic OpenWrt switches, 3-way switches, 1st-Gen dimmers, and mini plugs (`F7C030`, `WLS040` / `WLS0403`, `F7C059`, `F7C063`) include a built-in rules engine inside the switch. Newer RTOS models (`WDS060` Dimmer V2, `WSP080` Mini V2) do not expose on-device rule storage—though **Dimmer V2** still stores hardware **Night Mode (`20%`)** on-device.

During normal Wi-Fi discovery, WeMi inspects the switch info it already downloaded—adding **zero extra network delay**—and caches verified results per firmware version:

- **`On-Device Ready`** (plus **`• Long Press`** on wall switches and 1st-Gen dimmers that support 2-second wall-paddle holds): Verified on-device rules storage ready for reading and editing.
- **`Likely Supported`** / **`Likely Ready • Long Press`** or **`Likely Unsupported (RTOS)`**: Initial estimate from discovery before reading the switch directly.
- **`Not Checked`**: Shown for manually added switches until you tap **`Read`** or **`Inspect Rules`**.
- **`No On-Device Rules`**: Verified unsupported firmware (for `WDS060`, WeMi reminds you that **Dimmer Night Mode** is still stored on-device).
- **`Offline`**: Shown on switches that are currently unreachable on your local Wi-Fi (automatically sorted to the bottom of the list).

> [!TIP]
> **Try It Without Hardware**: Enable **Demo Showroom Mode** on the **More** tab to explore sample Long Press, Auto-Off Delay, and Sunset schedules, test creating/editing/deleting rules, update Solar Location, and try Backup Restore safely offline.

---

## 2. Supported On-Device Rule Types

You can create, edit, pause/resume, and delete four native rule types directly on compatible switches:

| Rule Type | What It Does Inside the Switch | Key Options |
| :--- | :--- | :--- |
| **Long Press** | Hold the physical wall paddle for ~2 seconds to control one or more switches on your home Wi-Fi. *(Available on compatible wall switches and 1st-Gen dimmers.)* | Action (**`Toggle`**, **`Turn Off`**, **`Turn On`**) and one or more **Target Switches** (shown by friendly switch name). |
| **Auto-Off Delay** | Automatically turns the switch **OFF** after it stays **ON** for your chosen duration—whether turned on from the wall, a widget, or the app. | Delay from **`1` to `1440` minutes** (quick presets: `5m`, `15m`, `30m`, `1h`, `2h`) and active days (`Every Day`, `Weekdays`, `Weekends`, or individual days). |
| **Schedule** | Turns the switch **ON → OFF** (`Mode: Turn ON → then OFF`) or **OFF → ON** (`Mode: Turn OFF → then ON`) across a daily or weekly time window. | Fixed clock times with wrapping steppers (`-1h`, `+1h`, `-15m`, `+15m`, `-5m`, `+5m`, plus fine `-1m` / `+1m`) or on-device **`Sunrise`** / **`Sunset`** with 7 offset presets (`-60m`, `-30m`, `-15m`, `Exact`, `+15m`, `+30m`, `+60m` — preserving any custom firmware offset), distinct fixed start/end time check, and active days. |
| **Timed Action** | Runs a single **Turn ON** or **Turn OFF** action at a chosen time. | Fixed clock time steppers or **`Sunrise`** / **`Sunset`** with 7 offset presets (`-60m`..`+60m`), **`Action: Turn Switch ON`** / **`Action: Turn Switch OFF`**, and active days. |

*(Pre-existing single-switch firmware rules such as **`Away Mode`** or **`Motion / Sensor`** rules show a **`View only`** badge, stay preserved safely in switch memory, and can be paused/resumed or deleted directly. Rules marked **`Shared rule`** (spanning multiple switches) or **`Target`** (controlled by another switch) are shown **read-only** with their edit, delete, and toggle controls disabled on that switch card so single-switch writes never corrupt multi-switch rules; manage **`Target`** rules from their owner switch.)*

---

## 3. Reading, Creating, Editing & Managing Rules

### Reading Existing Rules
- **No Switches Yet**: If your active Wi-Fi Home has no switches yet, tap **`Go to Devices`** right from the **Rules** tab to discover or add switches first.
- **Read Every Switch at Once**: Open the **Rules** tab and tap **`Read Rules`** (changes to **`Refresh All`** once snapshots are loaded) to read stored rules from all reachable switches on your active Wi-Fi Home. Switches with stored rules sort to the top automatically.
- **Read a Single Switch**: Tap **`Read`** (or **`Refresh`**) on any reachable switch card on the **Rules** tab—or expand a switch row on the **Devices** tab and tap **`Inspect Rules`** to preview up to 4 rule cards, tap **`Add Rule`**, **`Sync Clock`**, **`Manage Rules`**, or tap the clickable **`+N more rule(s) in Rules tab`** link to jump straight to full rule management.

### Creating or Editing an On-Device Rule
1. Tap **`New Rule`** at the top of the **Rules** tab, tap **`+`** in the top header while on the **Rules** tab, or tap **`Add Rule`** on a specific switch card (or inside a switch's expanded drawer on the **Devices** tab).
   - To edit an existing rule, tap the **`✎` (Edit)** icon on its rule card.
2. Choose the **Switch** (when opened from the top button), enter a **`Rule Name`**, and pick a **`Rule Type`** (**`Long Press`**, **`Auto-Off Delay`**, **`Schedule`**, or **`Timed Action`**).
3. Configure the action (`Action: Turn Switch ON` / `Action: Turn Switch OFF`, `Mode: Turn ON → then OFF` / `Mode: Turn OFF → then ON`, or Long Press `Toggle` / `Turn Off` / `Turn On`), target switches, delay minutes, or start/end times and active days, then choose **`Rule Active on Switch`** or **`Rule Paused`**.
4. Tap **`Save to Switch`**.

### Pausing, Resuming & Deleting Rules
- **Enable / Disable Switch**: Toggle any single-switch rule owned by that switch ON or OFF directly; paused rules display a **`• Paused`** badge.
- **`🗑` (Delete)**: Opens a confirmation dialog before removing the rule from switch flash memory.
- **Shared & Target Rules (`Shared rule` / `Target`)**: Multi-switch rules and rules owned by another switch stay read-only on that switch card (with toggle and delete controls disabled) to prevent accidental cross-switch corruption.

---

## 4. Switch Clock Sync & Offline Solar Location

Because Belkin's cloud servers are discontinued, a switch that loses power can boot with its internal clock reset (for example, back to the year 2000), which prevents scheduled rules from running on time.

- **Automatic Clock Sync**: Whenever WeMi detects clock drift (`Switch clock needs sync`) and you save a rule or update Solar Location, WeMi automatically syncs your phone's local UTC time and timezone offset to the switch first.
- **Manual Clock Sync**: Tap **`Sync Clock`** (or **`Clock Sync`**) on any compatible switch card—or inside its expanded drawer on the **Devices** tab—to sync the switch's clock immediately without editing rules.
- **Offline Solar Location (No Location Permission Needed)**: For accurate on-device **`Sunrise`** and **`Sunset`** schedules, tap **`Solar Location`** on a switch card, pick one of the **10 built-in city presets** (*San Francisco*, *Los Angeles*, *Seattle*, *Denver*, *Austin*, *Chicago*, *New York*, *Toronto*, *London*, *Sydney*) or enter signed latitude (`-90` to `90`) and longitude (`-180` to `180`, including negative `-` coordinates for Western and Southern hemispheres), and tap **`Save Location`**.

---

## 5. Built-In Anti-Brick Protection & One-Tap Backup Restore

Every write to a switch's on-device rules archive follows a strict safety pipeline designed to protect older flash hardware:

1. **Automatic Pre-Write Backup**: Before changing anything, WeMi saves an untouched copy of the switch's current rules archive strictly locally on your Android device (outside Android cloud Auto-Backup, retaining up to **5** recent backups per switch).
2. **In-Place Preservation**: WeMi modifies only the targeted rule rows while keeping all other rules, pre-existing firmware tables, and the switch's original archive filename intact.
3. **Pre-Upload Safety Gate**: Before sending data to the switch, WeMi sanitizes text fields, checks SQLite database integrity and rollback journal mode, enforces flash size limits and valid day/time ranges, and blocks invalid payloads automatically.
4. **Post-Upload Verification**: After uploading, WeMi reads the switch's live rules database version back to confirm the switch accepted and reloaded the new version cleanly.
5. **One-Tap Restore (With Pre-Restore Backup)**: Tap **`Backups (<count>)`** on any switch card to inspect saved snapshots (with database version and timestamp) and tap **`Restore`** anytime—WeMi automatically backs up your switch's current rules first, validates SQLite integrity, and increments the database version safely.
