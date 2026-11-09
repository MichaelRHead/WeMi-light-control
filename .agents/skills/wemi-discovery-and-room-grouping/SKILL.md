---
name: wemi-discovery-and-room-grouping
description: Internal Belkin WeMo SSDP/unicast device discovery logic, 3-tier hardware identity resolution (matchesDiscoveredDevice), automatic room inference (normalizeDeviceNameForInference & inferRoomFromName), and non-destructive legacy room migration (resolveDeviceRoomAssignment). EXCLUSIVELY OWNED by the Antigravity Device Discovery & Room Grouping Agent (other agents MUST read and follow this skill when modifying discovery or room grouping, but MUST NOT edit this skill or DISCOVERY_AND_ROOM_GROUPING.md).
---

# WeMi Device Discovery & Automatic Room Grouping Skill (`wemi-discovery-and-room-grouping`)

> [!CAUTION]
> **EXCLUSIVE AGENT OWNERSHIP NOTICE (READ-ONLY FOR OTHER AGENTS)**:
> - **Owner**: **Antigravity Device Discovery & Room Grouping Agent — SOLE AUTHOR & MAINTAINER**.
> - **Policy for All Other Agents (including the Google AI Studio Agent, the Antigravity Debugging & Support Agent, CLI coding agents, and external assistants)**: **DO NOT MODIFY, OVERWRITE, RENAME, OR DELETE `.agents/skills/wemi-discovery-and-room-grouping/SKILL.md` OR [`DISCOVERY_AND_ROOM_GROUPING.md`](../../DISCOVERY_AND_ROOM_GROUPING.md).**
> - All other agents **must** read, understand, and obey the discovery orchestration, 3-tier identity resolution, and automatic room inference/migration invariants documented here when editing repository, network, or UI code, but must treat these files as **strictly read-only**.

For the complete architecture specification, Mermaid flowcharts, and keyword precedence tables, read [`../../DISCOVERY_AND_ROOM_GROUPING.md`](../../DISCOVERY_AND_ROOM_GROUPING.md) and the companion user guides [`../../docs/user-guide/room-grouping.md`](../../docs/user-guide/room-grouping.md) and [`../../docs/user-guide/switch-discovery.md`](../../docs/user-guide/switch-discovery.md).

---

## 1. Core Responsibilities & Domain Ownership

The **Antigravity Device Discovery & Room Grouping Agent** owns the internal logic connecting raw LAN UPnP/SSDP responses to organized, human-meaningful rooms in Room v5 SQLite:

1. **Multi-Stage SSDP & Unicast Discovery Orchestration ([`WeMoDiscoveryCoordinator.kt`](../../app/src/main/java/com/example/data/WeMoDiscoveryCoordinator.kt), [`WeMoSsdpDiscoveryEngine.kt`](../../app/src/main/java/com/example/network/WeMoSsdpDiscoveryEngine.kt))**:
   - Coordinates UDP multicast `M-SEARCH` (`239.255.255.250:1900` with `MulticastLock` + 1s retransmit), loopback UDP test mirroring (`registerLoopbackSsdpPort`), known-device 9-port unicast verification, and `/24` subnet unicast sweeps (`Semaphore(24)`).
   - Serializes device adoption via `adoptMutex` and bounds all SOAP `GetBinaryState` probes with the shared `Semaphore(3)` so 2nd-Gen RTOS switches (`WLS040` / `WLS0403` / `WDS060`) never drop TCP connections.
2. **Deterministic 3-Tier Hardware Identity Resolution (`matchesDiscoveredDevice` & `findMatchingExistingDevice`)**:
   - **Tier 1 (`<serialNumber>`)**: If both `existing.serialNumber` and `discovered.serialNumber` are non-blank, they match **if and only if** `existing.serialNumber.equals(discovered.serialNumber, ignoreCase = true)`. Never cross-match two switches with different serial numbers even if DHCP swapped their IP addresses or if multiple loopback simulators run on `127.0.0.1`.
   - **Tier 2 (`<UDN>` / `USN`)**: If serial numbers are not both present and both carry a UDN/USN, matches if `discovered.usn.contains(existing.udn, ignoreCase = true)`.
   - **Tier 3 (Endpoint / `<friendlyName>` Fallback)**: Used **only** when at least one serial number is blank (e.g., an unprobed manual IP entry), matching by `(ipAddress, port)` or case-insensitive `name`.
3. **Automatic Room Inference & Non-Destructive Legacy Room Migration ([`WeMoEntities.kt`](../../app/src/main/java/com/example/data/WeMoEntities.kt))**:
   - **`normalizeDeviceNameForInference(deviceName)`**: Splits CamelCase boundaries (`"StairwayLight"` → `"stairway light"`, `"MasterBathVanity"` → `"master bath vanity"`) and replaces `_`, `-`, `/`, `.` with spaces before lowercase token matching.
   - **`inferRoomFromName(deviceName)`**: Evaluates room categories in specificity order:
     1. **`"Kitchen"`** (`"kitchen"`, `"pantry"`, `"island"`, `"breakfast"`, `"nook"`, `"undercabinet"`, `"under cabinet"`, `"countertop"`)
     2. **`"Dining Room"`** (`"dining"`, `"buffet"`, `"hutch"`, `"china cabinet"`)
     3. **`"Ensuite"`** (`"ensuite"`, `"en suite"`, `"bath"`, `"powder"`, `"vanity"`, `"restroom"`, `"lavatory"`, `"shower"`, `\bwc\b`) — **must precede `"Bedroom"`** so `"Master Bath"`, `"Guest Bathroom"`, `"Primary Bath"`, and `"Kids Bath"` resolve to **`"Ensuite"`**.
     4. **`"Hallway & Landing"`** (`"hallway"`, `\bhall\b`, `"landing"`, `"stair"`, `"foyer"`, `"entry"`, `"vestibule"`, `"mudroom"`, `"mud room"`, `"corridor"`, `"breezeway"`)
     5. **`"Bedroom"`** (`"bedroom"`, `"bedside"`, `"nightstand"`, `"night stand"`, `"headboard"`, `"dresser"`, `"wardrobe"`, `\bbed\b`, `"crib"`, `"cradle"`, `"loft"`, `"master"`, `"primary"`, `"nursery"`, `"guest"`, `"bunk"`, `"dorm"`, `"girls"`, `\bgirl\b`, `"boys"`, `\bboy\b`, `"kids"`, `\bkid\b`, `"children"`)
     6. **`"Outdoor"`**, **`"Garage"`**, **`"Basement"`**, **`"Office"`**, **`"Living Room"`** (with word-bounded `\bden\b`)
     7. **`"Unassigned"`** fallback (sorted after all named rooms in [`MainActivity.kt`](../../app/src/main/java/com/example/MainActivity.kt)).
   - **`resolveDeviceRoomAssignment(existingRoomName, deviceName, discoveredRoomName, isDemoMode)`**:
     - Preserves custom user-assigned rooms (e.g., `"Porch & Entryway"`), preserves Demo Showroom rooms when `isDemoMode == true`, and automatically migrates Real LAN switches whose `existingRoomName` is blank, `"Unassigned"`, or stuck in legacy `"Living Room"` (when `inferredRoom != "Living Room"`).
4. **Zero-Permission Subnet Home Sync & Soft-AP SSID Shielding (`sanitizeAndResolveInitialLanHome`, `isWemoDeviceSetupSsid`)**:
   - Filters out Belkin soft-AP SSIDs (`WeMo.Switch.xxx`, `Wemo.LS3W.xxx`, `WeMo.Light.xxx`, `WeMo.Mini.xxx`) from `GetMetaInfo` field 4 and repairs any previously polluted `WeMoHomeNetworkEntity` or `WeMoDeviceEntity` rows on startup.

---

## 2. Five Non-Negotiable Discovery & Room Grouping Invariants

Whenever modifying `WeMoEntities.kt`, `WeMoDiscoveryCoordinator.kt`, `WeMoSsdpDiscoveryEngine.kt`, `WeMoRepository.kt`, or `MainActivity.kt`, verify all five invariants:

1. **Unrecognized Names Must Default to `"Unassigned"`, Never `"Living Room"`**:
   - Keep `else -> "Unassigned"` in `inferRoomFromName` and keep `"Unassigned"` sorted at the bottom of `availableRooms` and `groupedDevicesByRoom` in [`MainActivity.kt`](../../app/src/main/java/com/example/MainActivity.kt).
2. **`"Ensuite"` Must Be Evaluated Before `"Bedroom"`**:
   - Compound names like `"Master Bath Vanity"`, `"Guest Bathroom Light"`, and `"Kids Bath Fan"` must resolve to `"Ensuite"`, never `"Bedroom"`.
3. **Short Ambiguous Tokens Must Use Pre-Compiled Word-Boundary Regexes**:
   - Always use `WORD_BED_REGEX` (`\bbed\b`), `WORD_HALL_REGEX` (`\bhall\b`), `WORD_DEN_REGEX` (`\bden\b`), `WORD_GIRL_REGEX` (`\bgirl\b`), `WORD_BOY_REGEX` (`\bboy\b`), `WORD_KID_REGEX` (`\bkid\b`), and `WORD_WC_REGEX` (`\bwc\b`) after `normalizeDeviceNameForInference`.
4. **All Discovery & Reconnect Flows Must Use `matchesDiscoveredDevice` and `resolveDeviceRoomAssignment`**:
   - Never bypass serial-number mismatch protection or overwrite custom user room assignments in `executeSsdpScan`, `adoptDiscoveredDevice`, `reconnectAndRediscoverHome`, or `sanitizeAndResolveInitialLanHome`.
5. **Unit & Repository Migration Tests Must Pass (`26/26` across 7 suites)**:
   - Verify [`WeMoSkuAndCryptoTest.kt`](../../app/src/test/java/com/example/WeMoSkuAndCryptoTest.kt), [`WeMoSoapAndSelfHealingTest.kt`](../../app/src/test/java/com/example/WeMoSoapAndSelfHealingTest.kt), and [`WeMoHardwareCujEndToEndTest.kt`](../../app/src/test/java/com/example/WeMoHardwareCujEndToEndTest.kt) with `./gradlew testDebugUnitTest --rerun-tasks`.
