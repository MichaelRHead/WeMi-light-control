# Implementation Plan: Real Belkin WeMo LAN Discovery & Control (`pywemo` in Kotlin)

This document details the engineering plan for porting the Belkin WeMo discovery, port-probing, SOAP control, hardware classification, and local UPnP GENA push-subscription architecture from [`pywemo`](https://github.com/pywemo/pywemo) and Home Assistant's `wemo` integration into Kotlin for **WeMi Light Control**.

Related documentation in this repository:
- [`AGENTS.md`](../AGENTS.md) — Architecture & Agent Guide
- [`.agents/skills/wemi-light-control/SKILL.md`](../.agents/skills/wemi-light-control/SKILL.md) — Agent Skill Definition
- [`.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md`](../.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md) — Complete `pywemo` / Home Assistant UPnP, SSDP, SOAP, MetaInfo, and GENA Protocol Reference

---

## 1. Architecture Overview (`pywemo` → Kotlin Mapping)

| `pywemo` / Home Assistant Module | Responsibility | Kotlin Target in `WeMi-light-control` |
| :--- | :--- | :--- |
| `pywemo/ssdp.py` (`scan`) | UDP multicast `M-SEARCH` to `239.255.255.250:1900` (`ST: urn:Belkin:service:basicevent:1`), 1-second retransmission loop, `(udn, location)` deduplication | `WeMoNetworkClient.performSsdpDiscoveryScan` + Android `WifiManager.MulticastLock` |
| `pywemo/discovery.py` (`device_from_uuid_and_location`, `xsd_types.py`) | Fetch exact `LOCATION` URL (`/setup.xml` or `/Lightsetup.xml`), verify `<manufacturer>Belkin International Inc.</manufacturer>`, classify hardware by `<UDN>` & `<modelName>` | `WeMoNetworkClient.fetchAndParseSetupXml` + `classifyHardwareType` |
| `pywemo/ouimeaux_device/__init__.py` (`probe_wemo`, `PROBE_PORTS`, `reconnect_with_device`) | Probe ports `49153, 49152, 49154, 49151, 49155..49159` with UDN verification (`match_udn`), distinguish TCP connect timeout vs `ECONNREFUSED` vs HTTP read timeout, fallback to targeted SSDP scan by UDN | `WeMoNetworkClient.probeWemoPort` + `WeMoRepository.reconnectDevice` |
| `pywemo/ouimeaux_device/api/service.py` (`Session`, `Action`) | SOAP HTTP `POST` with `Connection: close` (no keep-alive thread starvation), SOAP fault parsing | `WeMoNetworkClient.executeSoapAction` |
| `pywemo/ouimeaux_device/switch.py` & `dimmer.py` | `GetBinaryState` / `SetBinaryState`, handle `<BinaryState>Error</BinaryState>` when already in target state, dimmer `<brightness>` (`1..100`) | `WeMoNetworkClient.getBinaryState` / `setBinaryState` |
| `pywemo/util.py` (`MetaInfo`, `signal_strength_to_dbm`) | `metainfo1#GetMetaInfo` (`MAC\|Serial\|SKU\|FW\|SSID\|Model`) and `basicevent1#GetSignalStrength` | `WeMoNetworkClient.getMetaInfo` / `getSignalStrength` |
| `pywemo/subscribe.py` (`SubscriptionRegistry`) & `ha_wemo/coordinator.py` | Local HTTP `NOTIFY` callback server + UPnP `SUBSCRIBE /upnp/event/basicevent1` (`TIMEOUT: Second-300`), `HTTP 412` recovery, `MAX_CONCURRENCY = 3` + per-device lock | `WeMoGenaSubscriptionManager` + `WeMoRepository` (`Semaphore(3)` & per-device `Mutex`) |

---

## 2. Phase-by-Phase Implementation Plan

### Phase 1: `pywemo`-Grade Network Engine (`WeMoNetworkClient.kt`)

1. **Full Port Range & Smart Port Probing (`probeWemoPort`)**:
   - Expand `PROBE_PORTS` to match `pywemo`:
     `listOf(49153, 49152, 49154, 49151, 49155, 49156, 49157, 49158, 49159)`.
   - Always probe the device's last known `port` first (`index 0`).
   - **UDN / Serial Verification (`expectedSerialOrUdn`)**: Fetch `/setup.xml` and verify `<serialNumber>` / `<UDN>` before accepting a port, preventing accidental control of a different WeMo if DHCP reassigned IPs.
   - **Distinguish Connect Timeout vs. Connection Refused vs. Read Timeout**:
     - If TCP `connect()` times out (`SocketTimeoutException` during connect), the host IP is offline on the LAN — abort probing the remaining 8 ports immediately so offline detection completes in ~1.2s instead of ~12s.
     - If TCP `connect()` throws `ConnectException` (`ECONNREFUSED`), the WeMo IP is alive on Wi-Fi, just not listening on that port — immediately try the next port in `PROBE_PORTS`.
     - If TCP connects but HTTP read times out (`SocketTimeoutException` on read), the old port is wedged (`pywemo` issue) — continue probing the remaining ports.

2. **Real SSDP Discovery with Periodic Retransmission & `MulticastLock` (`performSsdpDiscoveryScan`)**:
   - Acquire Android `WifiManager.MulticastLock` (`"wemi_ssdp_lock"`) during UDP multicast scanning and release in `finally`.
   - Bind the UDP `DatagramSocket` to the phone/TV's active LAN IPv4 `InetAddress` (`resolveLocalIpv4Address()`).
   - **1-Second `M-SEARCH` Retransmission Loop (`pywemo/ssdp.py`)**:
     - Over a `4.5s` scan window, set `socket.soTimeout = 1000`. Send `M-SEARCH` (`ST: urn:Belkin:service:basicevent:1` and `ST: upnp:rootdevice`), and re-send at the start of each 1-second slice so dropped Wi-Fi UDP packets do not miss devices.
   - **Follow Exact `LOCATION` URL & Manufacturer Validation**:
     - Fetch the exact URL in `LOCATION:` (handling both `/setup.xml` and `/Lightsetup.xml` on 1st-gen `F7C030` switches).
     - Validate that `<manufacturer>` contains `Belkin` or `<UDN>` / `<deviceType>` is a Belkin WeMo URN (`urn:Belkin:...`).
   - **Remove Synthetic SSDP Injections**:
     - Remove the hardcoded `"Patio String Lights"` (`$subnetPrefix.118`) injection and the loop that fabricated SSDP entries from `knownLocalDevices`.

3. **Hardware Classification, Live State & `GetMetaInfo` Telemetry**:
   - After parsing `setup.xml`, immediately call:
     1. `getBinaryState(ip, port)` to populate real initial `isOn`, `brightness` (`1..100` for `IN_WALL_DIMMER`), and `latencyMs`.
     2. `getMetaInfo(ip, port)` (`POST /upnp/control/metainfo1`, `urn:Belkin:service:metainfo:1#GetMetaInfo`) to parse `MAC|SerialNumber|DeviceSKU|FirmwareVersion|AccessPointSSID|ModelName`.
     3. `getSignalStrength(ip, port)` (`POST /upnp/control/basicevent1`, `urn:Belkin:service:basicevent:1#GetSignalStrength`) to populate real `signalStrengthPercent` (`0..100%`).
   - Classify `WeMoDeviceType` accurately across all 4 hardware families:
     - `IN_WALL_DIMMER`: `<UDN>` starts with `uuid:Dimmer` (`Dimmer-1_0` / `Dimmer-2_0`), `<modelName>` is `Dimmer` / `WDS060`, or SKU is `F7C059` / `WDS060`.
     - `THREE_WAY_SWITCH`: `<UDN>` starts with `uuid:Lightswitch-2_0` / `uuid:Lightswitch-3_0`, or SKU / `<modelName>` / `<modelDescription>` contains `WLS040`, `WLS0403`, or `3-Way`.
     - `IN_WALL_SWITCH`: `<UDN>` starts with `uuid:Lightswitch-1_0` or `<modelName>` is `LightSwitch` (`F7C030`).
     - `MINI_PLUG`: `<UDN>` starts with `uuid:Socket` or SKU / `<modelName>` is `Socket`, `Mini`, `F7C063`, `WSP080`.

4. **SOAP Control Quirks (`setBinaryState` & `getBinaryState`)**:
   - Always send `Connection: close` so we never tie up the WeMo's limited TCP listener threads.
   - **Handle `<BinaryState>Error</BinaryState>` on Redundant State Change (`pywemo/ouimeaux_device/switch.py`)**:
     - When `SetBinaryState(1)` is sent to a switch that is already ON (or `0` when already OFF), WeMo returns HTTP 200 with `<BinaryState>Error</BinaryState>`. Treat this as confirming `isOn` (or verify with `getBinaryState`) rather than failing.
   - **Dimmer Brightness (`pywemo/ouimeaux_device/dimmer.py`)**:
     - For `IN_WALL_DIMMER`, `brightness` must be `1..100` alongside `<BinaryState>1</BinaryState>`. If `brightness == 0` is requested, send `<BinaryState>0</BinaryState>`.
   - **Night Mode SOAP Support (`GetNightModeConfiguration` / `ConfigureNightMode`)**:
     - Add `setNightModeConfiguration(ip, port, enabled, nightBrightness = 20)` on `basicevent1` so toggling Night Mode on a Dimmer sends the real SOAP command.

---

### Phase 2: Real Reachability, Two-Stage Reconnect & Concurrency Control (`WeMoRepository.kt`)

1. **Concurrency Limiting (`Semaphore(3)` + Per-Device `Mutex`)**:
   - Match Home Assistant's `MAX_CONCURRENCY = 3` and per-device lock so batch Room controls or home-wide refreshes never overwhelm WeMo switches.
2. **Honor Real `reachableOnLan` Status**:
   - Remove the mock fallbacks in `toggleDevicePower`, `updateDeviceBrightness`, `reconnectDevice`, `pingDeviceLatency`, and `reconnectAndRediscoverHome` that forced `isReachable = true` when `result.reachableOnLan == false`.
   - When a SOAP call fails on the current port, automatically run `pywemo`'s two-stage reconnect:
     1. **Stage 1 (Port Re-Probe)**: Probe `PROBE_PORTS` on `device.ipAddress` (verifying `serialNumber`).
     2. **Stage 2 (Targeted SSDP by Serial/UDN)**: If port probing fails, run a fast SSDP scan matching `serialNumber` in case DHCP changed the device's IP address.
     3. If reconnected, retry the SOAP action once on the newly discovered `ip:port` and persist the healed `ipAddress`, `port`, `isReachable = true`, and `latencyMs`.
     4. If still unreachable, update Room with `isReachable = false, latencyMs = 0, lastCheckedTime = "Offline"`.
3. **Deduplication by `serialNumber` / `UDN` (`adoptDiscoveredDevice`)**:
   - Match existing devices in Room by `serialNumber` first (then `ipAddress`), so re-scanning a network updates IP/port/state in place without duplicating devices or losing custom `roomName` assignments.
4. **Live Wi-Fi Subnet Detection & Demo Data Toggle**:
   - Detect the Android device's live IPv4 address and `/24` subnet prefix (`resolveLocalIpv4Address()`), matching or creating the active `WeMoHomeNetworkEntity` and allowing optional Demo Mode seeding.

---

### Phase 3: Local Push Updates via UPnP GENA (`WeMoGenaSubscriptionManager.kt`)

- Implement a lightweight coroutine-based `WeMoGenaSubscriptionManager` modeled on `pywemo/subscribe.py`:
  - Binds a local HTTP `ServerSocket` on an available high port (`8989..9050` or ephemeral port).
  - Sends `SUBSCRIBE /upnp/event/basicevent1` with `CALLBACK: <http://<local_ip>:<port>/sub/<deviceId>>`, `NT: upnp:event`, `TIMEOUT: Second-300` to each online WeMo device.
  - Renews subscriptions before expiry and handles `HTTP 412 Precondition Failed` (device rebooted) by unsubscribing and re-subscribing.
  - Parses incoming `NOTIFY` XML `<e:propertyset>` events (`<BinaryState>` and `<Brightness>`) and immediately updates Room so physical wall-switch presses (including 3-way switch flips) reflect in the UI within milliseconds.

---

### Phase 4: Verification & Automated Tests

- Expand unit and Robolectric tests in `ExampleRobolectricTest.kt` and `WeMoProtocolTest.kt` using a local loopback socket server to verify:
  - `/setup.xml` & `/Lightsetup.xml` parsing and hardware classification for `F7C030` (`IN_WALL_SWITCH`), `WLS040`/`WLS0403` (`THREE_WAY_SWITCH`), `F7C059`/`WDS060` (`IN_WALL_DIMMER`), and `F7C063`/`WSP080` (`MINI_PLUG`).
  - Port failover across `49151..49159` with UDN verification.
  - Handling of `<BinaryState>Error</BinaryState>` when a switch is already in the target state, pipe-delimited `<BinaryState>1|...</BinaryState>`, and dimmer `<brightness>` payloads.
  - Genuine offline (`isReachable = false`) reporting when a device is unreachable.
  - GENA `SUBSCRIBE` / `NOTIFY` event parsing.
