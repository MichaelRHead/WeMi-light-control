# Rules, Schedules & Auto-Off Timers

[← Back to User Guide Index](../../USER_GUIDE.md)

The **Rules** tab separates automation into two clear tiers—all running over your home Wi-Fi with no cloud account or external server required:
1. **On-Device Switch Rules (`Read-Only`)** — *"Stored in switch memory • runs even when your phone is off"*
2. **Local Switch Rules & Timers (`App-Run`)** — *"Runs from this device over local Wi-Fi • create & edit rules here"*

---

## 1. Inspecting On-Device Switch Rules (`Read-Only`)

Many classic switches, dimmers, and smart plugs (`F7C030`, `WLS040` / `WLS0403`, `F7C059`, `F7C063`) store automation rules directly inside the physical switch so they keep running even when your phone leaves the house or is turned off. Newer models (`WDS060` Dimmer V2, `WSP080` Mini V2) do not support on-device rule storage—though **Dimmer V2** still stores hardware **Night Mode** on-device.

> [!NOTE]
> **Safe Read-Only Inspection**: WeMi currently inspects on-device rules in **`Read-Only`** mode so you can see what is already stored inside your switches without risking changes to switch firmware. You can also try it right away in **Demo Showroom Mode** using sample on-device rules.

### How to Check Your Switches
- **At-a-Glance Header**: At the top of the **Rules** tab, **On-Device Switch Rules** (marked **`Read-Only`** with *"Stored in switch memory • runs even when your phone is off"*) shows `<N> ready • <M> unsupported • <Total> switches` before scanning, and updates to **`<R> rule(s) read • <N> ready • <M> unsupported`** once you read any switch. *(Motion Sensors are omitted because they do not store switch rules.)*
- **Read Every Switch at Once**: Tap **`Read Rules`** to expand your switch list and read stored rules from all reachable switches on the active Wi-Fi Home.
- **Expand or Collapse the Switch List Anytime**: Tap **`Show Switches (<count>)`** to browse individual switches without scanning everything, or tap **`Hide Switches`** to collapse the list while keeping your cached rule snapshots intact (showing `<R> on-device rule(s) read across <K> switch(es)`). Inside the expanded list, switches are automatically sorted by priority—switches with stored rules first, followed by reachable ready, unchecked, and unsupported switches, with **`Offline`**-badged switches last—and you can tap **`Read`** (or **`Refresh`** after reading) on any reachable switch.
- **Quick Preview from the Devices Tab**: You can also tap the body of any reachable switch on the **Devices** tab and tap **`Inspect Rules`** (or **`Refresh`**) to preview `DB v...`, any clock-sync warning, and up to 4 structured `trigger → action` rule cards inline (with **`• Paused`** badges and a **`+N more rule(s) in Rules tab`** overflow cue).
- **Zero Extra Scan Delay**: During normal Wi-Fi discovery, WeMi inspects the switch details it already downloaded—adding zero extra network requests—and caches verified results per firmware version so unsupported switches aren't re-checked unnecessarily.

### Capability Badges & Decoded Details
- **Capability Badges**:
  - **`Likely Supported`** / **`Likely Ready • Long Press`** or **`Likely Unsupported (RTOS)`**: Initial estimate from discovery before reading the switch directly.
  - **`Not Checked`**: Shown for manually added switches until you tap **`Read`** or **`Inspect Rules`**.
  - **`On-Device Ready`** (plus **`• Long Press`** on wall switches and 1st-Gen dimmers that support 2-second wall-paddle holds): Verified on-device rules storage.
  - **`No On-Device Rules`**: Verified unsupported firmware (for `WDS060`, WeMi reminds you that **Dimmer Night Mode** is still stored on-device).
  - **`Offline`**: Shown on switches that are currently unreachable on your local Wi-Fi (sorted at the bottom of the switch list).
- **Stored On-Device Rules**: Displays decoded **`Long Press`** rules (automatically turning hardware IDs into your friendly switch names), **`Auto-Off Delay`** timers, **`Schedule`** intervals (including on-device **`Sunrise`** / **`Sunset`** offsets and the switch's stored **`Solar location on switch`**), **`Timed Action`** rules, **`Away Mode`** occupancy rules, and **`Motion / Sensor`** triggers—marking disabled rules as **`• Paused`** and showing the database version (`DB v...`).
- **Switch Clock Warning**: If a switch lost power and its internal clock drifted or reset (for example, back to the year 2000 after Belkin's cloud shutdown), WeMi highlights **`Switch clock needs sync`**.
- **Automatic Local Backup**: Each time WeMi reads a valid rules archive from a switch, it keeps a rotating local backup of the untouched original file on your Android device ahead of future on-device rule editing.

---

## 2. Local Switch Rules & Timers (`App-Run`)

> [!NOTE]
> **How `App-Run` Rules Work**: Separated below the on-device inspector, **Local Switch Rules & Timers** (marked **`App-Run`** with *"Runs from this device over local Wi-Fi • create & edit rules here"*) executes directly from your Android device over your home Wi-Fi while WeMi is running. Each rule stays isolated to the Wi-Fi Home where its switch lives, so two switches with the same name in different homes never interfere with each other.

**WeMi Light Control** currently supports three kinds of **`App-Run`** local automation rules:

| Rule Type | Icon | How It Works | Example Trigger |
| :--- | :--- | :--- | :--- |
| **Schedule** | Calendar / Clock | Runs daily at the clock time you type (`Daily at 6:30 PM`, `07:00`, `11:15 PM`, or `Daily at Sunset (5:15 PM)`). If you type a keyword without a specific clock time, WeMi uses built-in defaults: **`Sunset` (`5:30 PM`)**, **`Sunrise` (`6:30 AM`)**, and **`Midnight` (`12:00 AM`)**. | `Daily at 6:30 PM` or `Sunset` |
| **Auto-Off Timer** | Stopwatch / Timer | Starts a live countdown whenever the target switch turns **ON** (whether toggled in the app, via a Home Screen widget, or pressed physically on the wall) and turns the switch **OFF** automatically when the timer reaches `0:00`. | `After 30 minutes ON` or `15 min` |
| **Motion Trigger** | Motion Sensor Waves | Triggers the chosen switch or dimmer (including a custom brightness level for dimmers) whenever a Motion Sensor in the same Wi-Fi Home senses movement. | `When Hallway Motion senses movement` |

---

## 3. Live Auto-Off Countdown Badges

When an **Auto-Off Timer** rule is enabled for a switch:
- As soon as the switch turns **ON**, WeMi starts a live second-by-second countdown (supporting durations from `1` minute up to `1440` minutes / 24 hours).
- On the **Devices** tab, the switch row displays a live countdown (`Auto-Off 29:54`, `Auto-Off 29:53`, …) right beside its **`ON`** status pill.
- Turning the switch **OFF** manually—or switching off the Auto-Off rule—cancels the countdown immediately.

---

## 4. Creating a New Local App Rule

1. Open the **Rules** tab from the bottom navigation bar and tap **`New Rule`** (or tap **`+`** in the top header while on the **Rules** tab).
2. In the **New Smart Switch Rule** dialog:
   - **`Rule Name`**: Give your rule a clear name (for example, `Porch Sunset Timer` or `Hallway 15m Auto-Off`). If left blank, WeMi names it `<Switch Name> Rule`.
   - **`Rule Type`**: Choose **`Schedule`**, **`Motion Trigger`**, or **`Auto-Off Timer`**. *(Selecting **`Auto-Off Timer`** automatically sets the default action to **Turn Switch OFF**.)*
   - **`Target Switch / Bulb`**: Tap the chip for the switch or dimmer in your active Wi-Fi Home that you want this rule to control.
   - **`When / Trigger Condition`**:
     - For **Schedules**, enter a clock time like `Daily at 6:30 PM`, `22:30`, `Daily at Sunset (5:15 PM)`, or a keyword (`Sunset`, `Sunrise`, `Midnight`).
     - For **Auto-Off Timers**, enter a duration in minutes such as `After 30 minutes ON` or `15 min`.
   - **`Action: Turn Switch ON` / `Action: Turn Switch OFF`**: Toggle whether the rule turns the switch **ON** or **OFF**.
   - **`Target Brightness` (Dimmers Only)**: When turning a dimmer **ON**, drag the slider (`1%–100%`) to choose the exact brightness level the rule applies.
3. Tap **`Save`**.

---

## 5. Testing & Managing Local App Rules

Each row on the **Rules** tab has three controls on the right:
- **`▶` (Play Button — Test Rule Now)**: Runs the rule immediately against the target switch on your local Wi-Fi so you can verify it works without waiting for the scheduled time.
- **Enable / Disable Switch**: Turns the rule on or off. Disabling an active **Auto-Off Timer** cancels its running countdown immediately.
- **`🗑` (Trash Icon)**: Deletes the rule.

*(Tip: To test a **Motion Trigger** rule, tap a Motion Sensor row on the **Devices** tab to **Refresh Motion** in Real LAN Mode or **Simulate Motion** in Demo Showroom Mode.)*
