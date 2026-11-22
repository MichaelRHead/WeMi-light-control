# Rules, Schedules & Auto-Off Timers

[← Back to User Guide Index](../../USER_GUIDE.md)

The **Rules** tab lets you automate switches, dimmers, and smart plugs from Belkin's discontinued smart switch platform directly over your local Wi-Fi network — with no cloud account or external server required.

> [!NOTE]
> **How Local Rules Run**: Schedules, Auto-Off countdowns, and Motion Trigger rules execute directly from your Android device over your home Wi-Fi while WeMi is running (they are not uploaded into the physical wall switch's internal memory). Each rule stays isolated to the Wi-Fi Home where its switch lives, so two switches with the same name in different homes never interfere with each other.

---

## 1. Types of Local Rules

**WeMi Light Control** supports three kinds of local automation rules:

| Rule Type | Icon | How It Works | Example Trigger |
| :--- | :--- | :--- | :--- |
| **Schedule** | Calendar / Clock | Runs daily at the clock time you type (`Daily at 6:30 PM`, `07:00`, `11:15 PM`, or `Daily at Sunset (5:15 PM)`). If you type a keyword without a specific clock time, WeMi uses built-in defaults: **`Sunset` (`5:30 PM`)**, **`Sunrise` (`6:30 AM`)**, and **`Midnight` (`12:00 AM`)**. | `Daily at 6:30 PM` or `Sunset` |
| **Auto-Off Timer** | Stopwatch / Timer | Starts a live countdown whenever the target switch turns **ON** (whether toggled in the app, via a Home Screen widget, or pressed physically on the wall) and turns the switch **OFF** automatically when the timer reaches `0:00`. | `After 30 minutes ON` or `15 min` |
| **Motion Trigger** | Motion Sensor Waves | Triggers the chosen switch or dimmer (including a custom brightness level for dimmers) whenever a Motion Sensor in the same Wi-Fi Home senses movement. | `When Hallway Motion senses movement` |

---

## 2. Live Auto-Off Countdown Badges

When an **Auto-Off Timer** rule is enabled for a switch:
- As soon as the switch turns **ON**, WeMi starts a live second-by-second countdown (supporting durations from `1` minute up to `1440` minutes / 24 hours).
- On the **Devices** tab, the switch row displays a live countdown (`Auto-Off 29:54`, `Auto-Off 29:53`, …) right beside its **`ON`** status pill.
- Turning the switch **OFF** manually—or switching off the Auto-Off rule—cancels the countdown immediately.

---

## 3. Creating a New Rule

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

## 4. Testing & Managing Existing Rules

Each row on the **Rules** tab has three controls on the right:
- **`▶` (Play Button — Test Rule Now)**: Runs the rule immediately against the target switch on your local Wi-Fi so you can verify it works without waiting for the scheduled time.
- **Enable / Disable Switch**: Turns the rule on or off. Disabling an active **Auto-Off Timer** cancels its running countdown immediately.
- **`🗑` (Trash Icon)**: Deletes the rule.

*(Tip: To test a **Motion Trigger** rule, tap a Motion Sensor row on the **Devices** tab to **Refresh Motion** in Real LAN Mode or **Simulate Motion** in Demo Showroom Mode.)*
