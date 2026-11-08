# Belkin WeMo Local UPnP / SSDP / SOAP Protocol Reference (`pywemo` & Home Assistant Spec)

This document details the local LAN protocol used by Belkin WeMo hardware, incorporating the exact discovery, port-probing, SOAP, MetaInfo, and GENA push-subscription mechanisms used by [`pywemo`](https://github.com/pywemo/pywemo) and Home Assistant's `wemo` integration.

---

## 1. Supported Hardware Models & UDN / SKU Classification

| Hardware Device | Model SKUs | `<modelName>` in `setup.xml` | `<deviceType>` URN in `setup.xml` | `<UDN>` Prefix (`discovery.py`) |
| :--- | :--- | :--- | :--- | :--- |
| **WeMo Light Switch (1st Gen)** | `F7C030` | `LightSwitch` | `urn:Belkin:device:lightswitch:1` | `uuid:Lightswitch-1_0-...` |
| **WeMo Light Switch (2nd Gen / 3-Way)** | `WLS040`, `WLS0403` | `LightSwitch` / `WLS040` / `WLS0403` | `urn:Belkin:device:lightswitch:1` | `uuid:Lightswitch-2_0-...`, `uuid:Lightswitch-3_0-...`, or SKU `WLS040`/`WLS0403` |
| **WeMo Wi-Fi Smart Dimmer** | `F7C059` (Gen 1), `WDS060` (Gen 2 RTOS) | `Dimmer` / `WDS060` | `urn:Belkin:device:dimmer:1` | `uuid:Dimmer-1_0-...` (`F7C059`), `uuid:Dimmer-2_0-...` (`WDS060`) |
| **WeMo Mini Smart Plug** | `F7C063` (Gen 1), `WSP080` (Gen 2) | `Socket` / `WSP080` | `urn:Belkin:device:controllee:1` | `uuid:Socket-1_0-...` |

### `pywemo` Classification & Validation Rules
1. **Manufacturer Check**: In `setup.xml` (or `/Lightsetup.xml` on certain `F7C030` firmwares), `<manufacturer>` is `Belkin International Inc.`. Validate this tag (or `urn:Belkin:` service/device URNs) so non-WeMo UPnP devices on the LAN (routers, TVs, Hue bridges, Sonos) are never misclassified as WeMo switches.
2. **Exact Hardware SKU & Wi-Fi SSID via `metainfo1#GetMetaInfo`**:
   - Calling `GetMetaInfo` on `/upnp/control/metainfo1` (`urn:Belkin:service:metainfo:1#GetMetaInfo`) returns `<MetaInfo>` formatted as 6 pipe-delimited fields:
     ```
     MAC|SerialNumber|DeviceSKU|FirmwareVersion|AccessPointSSID|ModelName
     ```
   - `DeviceSKU` (`values[2]`) identifies `F7C030` (Gen 1 Switch) vs `WLS040`/`WLS0403` (Gen 2 3-Way Switch) vs `F7C059`/`WDS060` (Dimmer) vs `F7C063`/`WSP080` (Mini Plug).
   - `AccessPointSSID` (`values[4]`) reports the exact 2.4 GHz Wi-Fi SSID the WeMo device is joined to, even if Android OS permissions restrict reading the phone's current SSID.

---

## 2. SSDP Multicast Discovery (`pywemo/ssdp.py`)

### Android & Socket Requirements
- Acquire `WifiManager.MulticastLock` (`wifiManager.createMulticastLock("wemi_ssdp").apply { setReferenceCounted(false); acquire() }`) before scanning and release it in `finally`.
- Bind the UDP `DatagramSocket` to the local IPv4 LAN interface address (`InetAddress.getByName(localIp)`) on an ephemeral port (`0`) so multicast packets route out the Wi-Fi interface rather than cellular/VPN.
- **Periodic Retransmission During Scan Window**:
  - UDP multicast packets are unacknowledged and frequently dropped on Wi-Fi.
  - `pywemo/ssdp.py` sets a `1000 ms` receive poll interval inside a `5000 ms` total discovery window: whenever a `1000 ms` slice finishes without exhausting the total timeout, re-send the `M-SEARCH` datagram to `239.255.255.250:1900`.
- **Deduplication Key**: Deduplicate SSDP responses by `(udn, location)` where `udn = usn.substringBefore("::")`.
- **Follow `LOCATION` Path Literally**: Always fetch the exact URL in the `LOCATION:` header (some `F7C030` firmwares return `http://<ip>:<port>/Lightsetup.xml` rather than `/setup.xml`).

### `M-SEARCH` Request Packet
```http
M-SEARCH * HTTP/1.1
ST: urn:Belkin:service:basicevent:1
MX: 1
MAN: "ssdp:discover"
HOST: 239.255.255.250:1900


```
*(Each line ends with `\r\n`, terminated by an empty line `\r\n`.)*

---

## 3. Port Probing & Two-Stage Reconnection (`pywemo/ouimeaux_device/__init__.py`)

### Full WeMo Port Range (`PROBE_PORTS`)
WeMo devices dynamically bind an HTTP server on one of 9 ports, ordered by frequency in `pywemo`:
```kotlin
val PROBE_PORTS = listOf(49153, 49152, 49154, 49151, 49155, 49156, 49157, 49158, 49159)
```

### Smart Port Probing (`probe_wemo`)
When probing `http://<host>:<port>/setup.xml` across `ports` (with the device's last-known `port` moved to index `0`):
1. **UDN Verification (`matchUdn`)**: Parse `<UDN>` (or `<serialNumber>`) from `setup.xml`. If `matchUdn != null && device.udn != matchUdn`, skip and continue! This prevents toggling the wrong device if DHCP reassigned the IP address to another WeMo unit.
2. **Distinguish `ConnectTimeoutException` vs. `ConnectException` vs. `SocketTimeoutException`**:
   - **`SocketTimeoutException` during TCP `connect()`**: The IP address itself is not reachable on the LAN (ARP/Wi-Fi timeout). Break early rather than waiting for 9 full timeouts on a dead IP.
   - **`ConnectException` (`ECONNREFUSED`)**: The host IP is alive and immediately sent a TCP `RST` on that port. Continue immediately to the next port in `PROBE_PORTS`.
   - **`SocketTimeoutException` during HTTP `read()`**: As noted in `pywemo`, WeMo firmware occasionally gets into a wedged state where an old port still accepts TCP connections (`SYN/ACK`) but never responds to HTTP requests while a newly bound port works. Continue probing the next port!

### Two-Stage Reconnect (`reconnect_with_device`)
When a SOAP request to a known device fails:
1. **Stage 1 — Port Re-Probing (`_reconnect_with_device_by_probing`)**: Probe `PROBE_PORTS` on the device's current IP address (verifying `serialNumber` / `UDN`).
2. **Stage 2 — Targeted SSDP Rediscovery (`_reconnect_with_device_by_discovery`)**: If port probing fails (e.g., router DHCP assigned a new IP), run an SSDP scan matching the device's `UDN` / `serialNumber` (`matchUdn`) to discover its new `ipAddress` and `port`.

---

## 4. HTTP / TCP Session & Concurrency Constraints (`pywemo/ouimeaux_device/api/service.py`)

- **Disable HTTP Keep-Alive (`Connection: close`)**:
  - WeMo embedded HTTP servers (OpenWrt `libupnp` and RTOS) have a very small thread pool and do not properly handle persistent HTTP keep-alive connections. Always send `Connection: close` and disconnect `HttpURLConnection` in `finally` to avoid exhausting TCP sockets on the switch.
- **Limit Concurrency (`MAX_CONCURRENCY = 3`) & Per-Device Mutex**:
  - Never flood a single WeMo device with parallel requests, and cap concurrent LAN-wide device initialization/polling to `3` coroutines (`Semaphore(3)`) with a per-device `Mutex`.

---

## 5. SOAP Services & Actions

### 5.1 `basicevent1` (`/upnp/control/basicevent1`, `urn:Belkin:service:basicevent:1`)

- **`GetBinaryState`**:
  - Returns `<BinaryState>` (`0` or `1`, or pipe-delimited `1|1492338954|...` — always take `.substringBefore("|").trim()`).
  - On WeMo Dimmers (`IN_WALL_DIMMER`), also returns `<brightness>` (`1..100`) and `<fader>`.
- **`SetBinaryState`**:
  - For Switches / Mini Plugs: `<BinaryState>0</BinaryState>` or `<BinaryState>1</BinaryState>`.
  - **Already-in-State `"Error"` Quirks (`pywemo/ouimeaux_device/switch.py`)**:
    - If a switch is *already* ON and receives `SetBinaryState(1)` (or already OFF and receives `SetBinaryState(0)`), WeMo firmware returns HTTP 200 with `<BinaryState>Error</BinaryState>`. Treat HTTP 200 with `<BinaryState>Error</BinaryState>` as confirming the requested state (or verify via `GetBinaryState`), NOT as a failure!
  - For Dimmers (`pywemo/ouimeaux_device/dimmer.py`):
    - WeMo ignores `<brightness>0</brightness>`. To set brightness `1..100`, send:
      ```xml
      <BinaryState>1</BinaryState>
      <brightness>75</brightness>
      ```
    - If brightness `0` is requested, send `<BinaryState>0</BinaryState>`.
- **`GetSignalStrength`**:
  - Returns `<SignalStrength>` (`0..100` percentage). `pywemo` converts this to RSSI dBm via:
    - `>= 100` -> `-50 dBm`
    - `24..99` -> `round((percent - 24) * 10.0 / 26.0 - 80)`
    - `1..23` -> `round(percent * 10.0 / 26.0 - 90)`
    - `<= 0` -> `-90 dBm`
- **`GetNightModeConfiguration` / `ConfigureNightMode` (Dimmer)**:
  - Controls Dimmer Night Mode (`nightMode`, `startTime`, `endTime`, `nightModeBrightness`).
- **`GetFriendlyName` / `ChangeFriendlyName`**:
  - Reads or updates the hardware's internal `<FriendlyName>`.

### 5.2 `metainfo1` (`/upnp/control/metainfo1`, `urn:Belkin:service:metainfo:1`)
- **`GetMetaInfo`**:
  - Returns `<MetaInfo>MAC|SerialNumber|DeviceSKU|FirmwareVersion|AccessPointSSID|ModelName</MetaInfo>`.

---

## 6. Local Push Event Subscriptions (`pywemo/subscribe.py` — UPnP GENA)

To receive instant push notifications when a physical wall switch, 3-way switch, dimmer, or mini plug button is pressed:

1. **Local HTTP Callback Server**:
   - Bind a lightweight `ServerSocket` on the Android device's Wi-Fi IP (e.g., port `8989` or ephemeral port).
2. **`SUBSCRIBE` Request**:
   ```http
   SUBSCRIBE /upnp/event/basicevent1 HTTP/1.1
   HOST: <device_ip>:<device_port>
   CALLBACK: <http://<android_ip>:<callback_port>/sub/basicevent/<device_serial>>
   NT: upnp:event
   TIMEOUT: Second-300
   Connection: close

   ```
   - Response headers contain `SID: uuid:...` and `TIMEOUT: Second-300`.
3. **Renewal & `412 Precondition Failed` Recovery**:
   - Before `TIMEOUT` expires (e.g. every `240s`), send `SUBSCRIBE` with `SID: <subscription_id>` and `TIMEOUT: Second-300` (no `CALLBACK` or `NT` header).
   - If the device responds with `HTTP/1.1 412 Precondition Failed` (e.g., after a reboot), send `UNSUBSCRIBE` with `SID`, clear `subscription_id`, and issue a fresh `SUBSCRIBE` with `CALLBACK` and `NT: upnp:event`.
4. **Incoming `NOTIFY` Callback**:
   - When state changes, the WeMo device sends an HTTP `NOTIFY` request to the callback path with XML body:
     ```xml
     <e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">
       <e:property>
         <BinaryState>1</BinaryState>
       </e:property>
       <e:property>
         <Brightness>80</Brightness>
       </e:property>
     </e:propertyset>
     ```
   - Reply `HTTP/1.1 200 OK` and immediately update Room (`isOn`, `brightness`, `isReachable = true`, `lastCheckedTime = "Push event"`).
