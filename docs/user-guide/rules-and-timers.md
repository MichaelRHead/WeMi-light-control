# Rules, Schedules & Auto-Off Timers

[← Back to User Guide Index](../../USER_GUIDE.md)

The **Rules** tab lets you automate your Belkin WeMo switches, dimmers, and plugs directly over your local Wi-Fi network — with zero dependency on external cloud servers.

---

## 1. Types of Local Rules

**WeMi Light Control** supports three categories of local automation rules:

| Rule Type | Icon | How It Works | Example Trigger |
| :--- | :--- | :--- | :--- |
| **Schedule (`SCHEDULE`)** | Calendar / Clock | Runs daily at a specified clock time (`6:30 PM`, `07:00`, `11:15 PM`) or solar keyword (`Sunset` = `6:00 PM`, `Sunrise` = `6:30 AM`). | `Daily at 6:30 PM` or `Sunset` |
| **Auto-Off Timer (`AUTO_OFF`)** | Stopwatch / Timer | Automatically starts a countdown whenever the target switch turns **ON** (whether toggled in the app or pressed physically on the wall) and turns the switch **OFF** when the timer reaches `00:00`. | `After 30 minutes ON` |
| **Motion Trigger (`MOTION_TRIGGER`)** | Motion Sensor Waves | Triggers the target switch or dimmer whenever a WeMo Motion Sensor detects movement. | `When Hallway Motion senses movement` |

---

## 2. Live Auto-Off Countdown Badges

When an **Auto-Off Timer** rule is enabled for a switch (or when a switch has a per-device `autoOffMinutes` timer configured):
- As soon as the switch turns **ON**, the scheduler starts a live second-by-second countdown.
- On the **Main Screen** (**Devices** tab), the switch row displays a live monospace countdown (`Auto-Off 29:54`, `Auto-Off 29:53`, …) right next to its **`ON`** power pill.
- If you manually turn the switch **OFF** before the timer expires, the countdown is automatically cancelled.

---

## 3. Creating a New Rule

1. Open the **Rules** tab from the bottom navigation bar and tap **`+ New Rule`** (or tap **`+`** in the top green header).
2. In the **New WeMo Switch Rule** dialog:
   - **Rule Name**: Give your rule a clear name (e.g., `Porch Sunset Timer` or `Hallway 15m Auto-Off`).
   - **Rule Type**: Select **`Schedule`**, **`Motion Trigger`**, or **`Auto-Off Timer`**.
   - **Target Switch / Bulb**: Tap the chip for the switch or dimmer you want this rule to control.
   - **When / Trigger Condition**:
     - For **Schedules**, enter a time like `Daily at 6:30 PM`, `10:45 PM`, `Sunset`, or `Sunrise`.
     - For **Auto-Off Timers**, enter a duration in minutes like `After 30 minutes ON` or `15 min`.
   - **Action (`Turn Switch ON` / `Turn Switch OFF`)**: Toggle the switch to choose whether the rule turns the target device **ON** or **OFF**.
   - **Target Brightness (Dimmers Only)**: If turning a dimmer **ON**, drag the slider (`1%–100%`) to set the exact brightness level the rule should apply.
3. Tap **`Save`**.

---

## 4. Managing & Testing Existing Rules

Each row on the **Rules** tab provides three quick controls on the right:
- **`▶` (Play Button — Test Rule Now)**: Immediately executes the rule's SOAP action against the target switch on your LAN so you can verify it works without waiting for the scheduled time.
- **Enable / Disable Switch**: Toggles the rule active or inactive. If you disable an **Auto-Off** rule while a countdown is running, the active countdown is cancelled immediately.
- **`🗑` (Trash Icon)**: Deletes the rule from the database.
