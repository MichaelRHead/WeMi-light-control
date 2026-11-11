---
name: wemi-discovery-and-room-grouping
description: Internal Belkin WeMo SSDP/unicast device discovery logic, 3-tier hardware identity resolution (matchesDiscoveredDevice), automatic room inference (normalizeDeviceNameForInference & inferRoomFromName), and non-destructive legacy room migration (resolveDeviceRoomAssignment). EXCLUSIVELY OWNED by the Antigravity Device Discovery & Room Grouping Agent (other agents MUST read and follow this skill when modifying discovery or room grouping, but MUST NOT edit this skill or DISCOVERY_AND_ROOM_GROUPING.md).
---

# WeMi Device Discovery & Automatic Room Grouping Skill (`wemi-discovery-and-room-grouping`)

> [!CAUTION]
> **EXCLUSIVE AGENT OWNERSHIP NOTICE (READ-ONLY FOR OTHER AGENTS)**:
> - **Owner**: **Antigravity Device Discovery & Room Grouping Agent — SOLE AUTHOR & MAINTAINER**.
> - **Policy for All Other Agents (including the Google AI Studio Agent, the Antigravity Telemetry & Debugging Agent, the Antigravity Code Quality & E2E Testing Agent, the Antigravity License & Brand Governance Agent, CLI coding agents, and external assistants)**: **DO NOT MODIFY, OVERWRITE, RENAME, OR DELETE `.agents/skills/wemi-discovery-and-room-grouping/SKILL.md` OR [`DISCOVERY_AND_ROOM_GROUPING.md`](../../DISCOVERY_AND_ROOM_GROUPING.md).**
> - All other agents **must** read, understand, and obey the discovery orchestration, 3-tier identity resolution, and automatic room inference/migration invariants documented here when editing repository, network, or UI code, but must treat these files as **strictly read-only**.

For the complete architecture specification, Mermaid flowcharts, and keyword precedence tables, read [`../../DISCOVERY_AND_ROOM_GROUPING.md`](../../DISCOVERY_AND_ROOM_GROUPING.md) and the companion user guides [`../../docs/user-guide/room-grouping.md`](../../docs/user-guide/room-grouping.md) and [`../../docs/user-guide/switch-discovery.md`](../../docs/user-guide/switch-discovery.md).

---

## 1. Core Responsibilities & Domain Ownership

The **Antigravity Device Discovery & Room Grouping Agent** owns the internal logic connecting raw LAN UPnP/SSDP responses to organized, human-meaningful rooms in Room v5 SQLite:

1. **Multi-Stage SSDP & Unicast Discovery Orchestration ([`DiscoveryCoordinator.kt`](../../app/src/main/java/org/suppressingfire/wemi/data/DiscoveryCoordinator.kt), [`DeviceCrudAndRulesCoordinator.kt`](../../app/src/main/java/org/suppressingfire/wemi/data/DeviceCrudAndRulesCoordinator.kt), [`SsdpDiscoveryEngine.kt`](../../app/src/main/java/org/suppressingfire/wemi/network/SsdpDiscoveryEngine.kt))**:
   - Coordinates UDP multicast `M-SEARCH` (`239.255.255.250:1900` with `MulticastLock` + retransmit), loopback UDP test mirroring (`registerLoopbackSsdpPort`), known-device 9-port unicast verification via first-party [`LanHttpClient.kt`](../../app/src/main/java/org/suppressingfire/wemi/network/LanHttpClient.kt) / [`NetworkClient.kt`](../../app/src/main/java/org/suppressingfire/wemi/network/NetworkClient.kt), manual-IP 9-port hardware enrichment (`saveDevice`), and `/24` subnet unicast sweeps (`Semaphore(24)` via Wi-Fi-bound `wifiSocketFactory`).
   - Serializes device adoption via `adoptMutex` and bounds all SOAP `GetBinaryState` probes with the shared `Semaphore(3)` so 2nd-Gen RTOS switches (`WLS040` / `WLS0403` / `WDS060`) never drop TCP connections.
2. **Deterministic 3-Tier Hardware Identity Resolution (`DeviceEntity.matchesDiscovered`, `matchesDiscoveredDevice` & `findMatchingExistingDevice`)**:
   - **Tier 1 (`<serialNumber>` via `!LanRuntimeUtils.isPlaceholderSerial`)**: If both `existing.serialNumber` and `discovered.serialNumber` are real non-placeholder serials (excluding blank and `221517K01018AB` / `LAN` / `NEW` in [`LanRuntimeUtils.kt`](../../app/src/main/java/org/suppressingfire/wemi/network/LanRuntimeUtils.kt)), they match **if and only if** `existing.serialNumber.equals(discovered.serialNumber, ignoreCase = true)`. Never cross-match two switches with different real serial numbers even if DHCP swapped their IP addresses or if multiple loopback simulators run on `127.0.0.1`.
   - **Tier 2 (`<UDN>` / `USN`)**: If real serial numbers are not both present and both carry a UDN/USN, matches if `discovered.usn.contains(existing.udn, ignoreCase = true)`.
   - **Tier 3 (Endpoint / `<friendlyName>` Fallback)**: Used **only** when at least one serial number is blank or a placeholder (e.g., an unprobed manual IP entry), matching by `(ipAddress, port)` or case-insensitive `name`.
3. **Automatic Room Inference & Non-Destructive Legacy Room Migration ([`DeviceEntities.kt`](../../app/src/main/java/org/suppressingfire/wemi/data/DeviceEntities.kt))**:
   - **`normalizeDeviceNameForInference(deviceName)`**: Splits CamelCase boundaries (`"StairwayLight"` → `"stairway light"`, `"MasterBathVanity"` → `"master bath vanity"`) and replaces `_`, `-`, `/`, `.` with spaces before lowercase token matching.
   - **`inferRoomFromName(deviceName)`**: Evaluates room categories in specificity order:
     1. **`"Kitchen"`** (`"kitchen"`, `"pantry"`, `"island"`, `"breakfast"`, `"nook"`, `"undercabinet"`, `"under cabinet"`, `"countertop"`)
     2. **`"Dining Room"`** (`"dining"`, `"buffet"`, `"hutch"`, `"china cabinet"`)
     3. **`"Ensuite"`** (`"ensuite"`, `"en suite"`, `"bath"`, `"powder"`, `"vanity"`, `"restroom"`, `"lavatory"`, `"shower"`, `\bwc\b`) — **must precede `"Bedroom"`** so `"Master Bath"`, `"Guest Bathroom"`, `"Primary Bath"`, and `"Kids Bath"` resolve to **`"Ensuite"`**.
     4. **`"Hallway & Landing"`** (`"hallway"`, `\bhall\b`, `"landing"`, `"stair"`, `"foyer"`, `"entry"`, `"vestibule"`, `"mudroom"`, `"mud room"`, `"corridor"`, `"breezeway"`)
     5. **`"Bedroom"`** (`"bedroom"`, `"bedside"`, `"nightstand"`, `"night stand"`, `"headboard"`, `"dresser"`, `"wardrobe"`, `\bbed\b`, `"crib"`, `"cradle"`, `"loft"`, `"master"`, `"primary"`, `"nursery"`, `"guest"`, `"bunk"`, `"dorm"`, `"girls"`, `\bgirl\b`, `"boys"`, `\bboy\b`, `"kids"`, `\bkid\b`, `"children"`)
     6. **`"Outdoor"`**, **`"Garage"`**, **`"Basement"`**, **`"Office"`**, **`"Living Room"`** (with word-bounded `\bden\b`)
     7. **`"Unassigned"`** fallback (sorted after all named rooms in [`MainActivity.kt`](../../app/src/main/java/org/suppressingfire/wemi/MainActivity.kt), [`TvScreen.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/components/TvScreen.kt), and [`TvHeadersAndScenes.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/components/TvHeadersAndScenes.kt)).
   - **`resolveDeviceRoomAssignment(existingRoomName, deviceName, discoveredRoomName, isDemoMode)`**:
     - Preserves custom user-assigned rooms (e.g., `"Porch & Entryway"`), preserves Demo Showroom rooms when `isDemoMode == true`, and automatically migrates Real LAN switches whose `existingRoomName` is blank, `"Unassigned"`, or stuck in legacy `"Living Room"` (when `inferredRoom != "Living Room"`).
4. **Zero-Permission Subnet Home Sync & Soft-AP SSID Shielding (`sanitizeAndResolveInitialLanHome`, `isWemoDeviceSetupSsid`)**:
   - Filters out Belkin soft-AP SSIDs (`WeMo.Switch.xxx`, `Wemo.LS3W.xxx`, `WeMo.Light.xxx`, `WeMo.Mini.xxx`) from `GetMetaInfo` field 4 and repairs any previously polluted `HomeNetworkEntity` or `DeviceEntity` rows on startup.

---

## 2. Five Non-Negotiable Discovery & Room Grouping Invariants

Whenever modifying `DeviceEntities.kt`, `DiscoveryCoordinator.kt`, `DeviceCrudAndRulesCoordinator.kt`, `LanRuntimeUtils.kt`, `SsdpDiscoveryEngine.kt`, `SwitchRepository.kt`, `MainActivity.kt`, `TvScreen.kt`, or `TvHeadersAndScenes.kt`, verify all five invariants:

1. **Unrecognized Names Must Default to `"Unassigned"`, Never `"Living Room"`**:
   - Keep `else -> "Unassigned"` in `inferRoomFromName` and keep `"Unassigned"` sorted at the bottom of `availableRooms` / `groupedDevicesByRoom` in [`MainActivity.kt`](../../app/src/main/java/org/suppressingfire/wemi/MainActivity.kt) and `groupedByRoom` (`TvRoomSectionHeader`) in [`TvScreen.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/components/TvScreen.kt) / [`TvHeadersAndScenes.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/components/TvHeadersAndScenes.kt).
2. **`"Ensuite"` Must Be Evaluated Before `"Bedroom"`**:
   - Compound names like `"Master Bath Vanity"`, `"Guest Bathroom Light"`, and `"Kids Bath Fan"` must resolve to `"Ensuite"`, never `"Bedroom"`.
3. **Short Ambiguous Tokens Must Use Pre-Compiled Word-Boundary Regexes**:
   - Always use `WORD_BED_REGEX` (`\bbed\b`), `WORD_HALL_REGEX` (`\bhall\b`), `WORD_DEN_REGEX` (`\bden\b`), `WORD_GIRL_REGEX` (`\bgirl\b`), `WORD_BOY_REGEX` (`\bboy\b`), `WORD_KID_REGEX` (`\bkid\b`), and `WORD_WC_REGEX` (`\bwc\b`) after `normalizeDeviceNameForInference`.
4. **All Discovery & Reconnect Flows Must Use `DeviceEntity.matchesDiscovered` (`!LanRuntimeUtils.isPlaceholderSerial`) and `resolveDeviceRoomAssignment`**:
   - Never bypass real-serial mismatch protection, never treat synthetic placeholder serials as real hardware serials, and never overwrite custom user room assignments in `executeSsdpScan`, `adoptDiscoveredDevice`, `reconnectAndRediscoverHome`, or `sanitizeAndResolveInitialLanHome`.
5. **Unit, Governance & E2E Tests Must Pass (`37/37` across 10 suites)**:
   - Verify [`ExampleUnitTest.kt`](../../app/src/test/java/org/suppressingfire/wemi/ExampleUnitTest.kt), [`SkuAndCryptoTest.kt`](../../app/src/test/java/org/suppressingfire/wemi/SkuAndCryptoTest.kt), [`SoapAndSelfHealingTest.kt`](../../app/src/test/java/org/suppressingfire/wemi/SoapAndSelfHealingTest.kt), [`RepositorySelfHealingTest.kt`](../../app/src/test/java/org/suppressingfire/wemi/RepositorySelfHealingTest.kt), [`ExampleRobolectricTest.kt`](../../app/src/test/java/org/suppressingfire/wemi/ExampleRobolectricTest.kt), [`TvAndHeaderLayoutE2ETest.kt`](../../app/src/test/java/org/suppressingfire/wemi/TvAndHeaderLayoutE2ETest.kt), and [`HardwareCujEndToEndTest.kt`](../../app/src/test/java/org/suppressingfire/wemi/HardwareCujEndToEndTest.kt) with `./gradlew testDebugUnitTest --rerun-tasks`.
