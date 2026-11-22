# Rules, Schedules & Auto-Off Timers

[← Back to User Guide Index](../../USER_GUIDE.md)

The **Rules** tab lets you inspect **On-Device Switch Rules** stored inside supported switches and manage local schedules, auto-off timers, and motion triggers over your home Wi-Fi — with no cloud account or external server required.

---

## 1. Inspecting On-Device Switch Rules (Read-Only)

Supported 1st-Gen and 2nd-Gen OpenWrt wall switches, dimmers, and plugs (`F7C030`, `WLS040` / `WLS0403`, `F7C059`, `F7C063`) run their own built-in rule engine inside the switch hardware so rules keep firing even when your phone leaves the house. Newer RTOS models (`WDS060` Dimmer V2, `WSP080` Mini V2) do not expose on-device rule storage (though Dimmer V2 still stores hardware **Night Mode** on-device).

### How WeMi Detects Capability Without Slowing Down Scans
- **Instant Discovery Hints**: When WeMi discovers or adds a switch, it reads the device descriptor you already fetched with zero extra network calls to label switches as **`Likely Supported`** or **`Likely Unsupported (RTOS)`**.
- **Verified Read-Only Inspection**: When you tap **`Read Rules`** on the **Rules** tab (or **`Inspect Rules`** inside any switch's expanded drawer on the **Devices** tab), WeMi verifies the switch's exact rule service actions and caches the result against its firmware version so unsupported switches are never re-probed unnecessarily.

### What You Can See on Each Switch
- **Capability Badges**: **`On-Device Ready`** (plus **`• Long Press`** on wall switches and 1st-Gen dimmers that support 2-second wall-paddle holds) or **`No On-Device Rules`** on RTOS firmware.
- **Stored On-Device Rules**: Displays existing **`Long Press`** rules (resolving target switch names automatically), **`Auto-Off Delay`** timers, **`Schedule`** intervals (including on-device **`Sunrise`** / **`Sunset`** offsets and stored solar location), **`Away Mode`** occupancy rules, and **`Motion / Sensor`** triggers, along with the switch's rules database version (`DB v...`).
- **Switch Clock Warning**: If a switch lost power and its internal clock drifted or reset (for example, back to the year 2000 without cloud NTP), WeMi flags **`Switch clock needs sync`**.
- **Automatic Local Backup**: Every time WeMi reads a valid rules archive from a switch, it keeps a rotating local backup of the untouched raw archive on your device in preparation for full on-device rule editing.

---

## 2. Local App Rules & Timers

> [!NOTE]
> **How Local App Rules Run**: The **Local Switch Rules & Timers** section below the on-device inspector executes directly from your Android device over your home Wi-Fi while WeMi is running. Each rule stays isolated to the Wi-Fi Home where its switch lives, so two switches with the same name in different homes never interfere with each other.

**WeMi Light Control** currently supports three kinds of local app automation rules:

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
