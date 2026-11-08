# Belkin WeMo Local UPnP / SSDP / SOAP Protocol Reference

This document details the local LAN protocol used by Belkin WeMo hardware (no cloud dependency required).

---

## 1. Supported Hardware Models & Identification

| Hardware Device | Model Numbers | `<modelName>` in `setup.xml` | `<deviceType>` URN in `setup.xml` | Serial / UDN Prefix |
| :--- | :--- | :--- | :--- | :--- |
| **WeMo Light Switch (1st Gen)** | `F7C030` | `LightSwitch` | `urn:Belkin:device:lightswitch:1` | `uuid:Lightswitch-1_0-...` |
| **WeMo Light Switch (2nd Gen / 3-Way)** | `WLS040`, `WLS0403` | `LightSwitch` or `WLS040` / `WLS0403` | `urn:Belkin:device:lightswitch:1` | `uuid:Lightswitch-1_0-...` or `uuid:Lightswitch-2_0-...` |
| **WeMo Wi-Fi Smart Dimmer** | `F7C059` (Gen 1), `WDS060` (Gen 2) | `Dimmer` or `WDS060` | `urn:Belkin:device:dimmer:1` | `uuid:Dimmer-1_0-...` or `uuid:Dimmer-2_0-...` |
| **WeMo Mini Smart Plug** | `F7C063` (Gen 1), `WSP080` (Gen 2) | `Socket` or `WSP080` | `urn:Belkin:device:controllee:1` | `uuid:Socket-1_0-...` |

### Heuristic Classification Rules
When parsing `/setup.xml`:
1. **`IN_WALL_DIMMER`**: `<deviceType>` contains `dimmer` OR `<modelName>` contains `Dimmer` or `WDS` OR `<UDN>` starts with `uuid:Dimmer`.
2. **`THREE_WAY_SWITCH`**: `<modelName>` / `<modelDescription>` / `<friendlyName>` / `<serialNumber>` contains `WLS0403`, `WLS040`, `3-Way`, `3Way`, or `Lightswitch-2_0`.
3. **`IN_WALL_SWITCH`**: `<deviceType>` contains `lightswitch` OR `<modelName>` contains `LightSwitch` OR `<UDN>` starts with `uuid:Lightswitch`.
4. **`MINI_PLUG`**: `<deviceType>` contains `controllee` or `socket` OR `<modelName>` contains `Socket`, `Mini`, or `WSP080` OR `<UDN>` starts with `uuid:Socket`.

---

## 2. SSDP Multicast Discovery (`239.255.255.250:1900`)

### Android Requirements
- Must hold `WifiManager.MulticastLock` (`wifiManager.createMulticastLock("wemi_ssdp").acquire()`) during discovery so the Wi-Fi interface forwards UDP packets from WeMo devices.
- Bind an ephemeral UDP `DatagramSocket` and send `M-SEARCH` datagrams to `239.255.255.250:1900`.

### `M-SEARCH` Request Packet
```http
M-SEARCH * HTTP/1.1
HOST: 239.255.255.250:1900
MAN: "ssdp:discover"
MX: 2
ST: urn:Belkin:service:basicevent:1

```
*(Note: Each line must end with `\r\n`, followed by a trailing `\r\n`. Sending a second packet with `ST: upnp:rootdevice` maximizes compatibility across firmware versions.)*

### `M-SEARCH` Response Example
```http
HTTP/1.1 200 OK
CACHE-CONTROL: max-age=86400
DATE: Sat, 07 Nov 2026 23:00:00 GMT
EXT:
LOCATION: http://192.168.1.104:49153/setup.xml
OPT: "http://schemas.upnp.org/upnp/1/0/"; ns=01
01-NLS: 905bfa3c-1dd2-11b2-8928-fd8585e43e32
SERVER: Unspecified, UPnP/1.0, Unspecified
X-User-Agent: redsonic
ST: urn:Belkin:service:basicevent:1
USN: uuid:Dimmer-1_0-221739K1300410::urn:Belkin:service:basicevent:1

```

---

## 3. UPnP Device Descriptor (`GET /setup.xml`)

- **Ports**: WeMo devices dynamically bind an HTTP server on one of ports **`49151` through `49156`** (most commonly `49153` or `49152`). Always use the port advertised in the SSDP `LOCATION` header first, and fall back to scanning `[49153, 49152, 49154, 49155, 49151, 49156]` if a device rebooted and switched ports.
- **Key XML Tags in `/setup.xml`**:
  - `<friendlyName>`: User-configured device name (e.g. `Dining Chandelier`).
  - `<modelName>`: `LightSwitch`, `Dimmer`, `Socket`, etc.
  - `<modelDescription>`: e.g. `Belkin Plugin Socket 1.0`, `Belkin WeMo Dimmer`.
  - `<serialNumber>`: Unique hardware serial (stable across IP/port changes).
  - `<UDN>`: Unique Device Name, e.g. `uuid:Lightswitch-1_0-221512K13008A2`.
  - `<firmwareVersion>`: e.g. `WeMo_WW_2.00.11532.PVT-OWRT-LS`.
  - `<macAddress>`: Device MAC (sometimes without colons, e.g. `6038E0AA382A`).
  - `<binaryState>`: Sometimes included in `setup.xml`, but **not reliable** for live state—always follow up with a `GetBinaryState` SOAP call.

---

## 4. SOAP Control Endpoint (`POST /upnp/control/basicevent1`)

All four device types support the `urn:Belkin:service:basicevent:1` service at `/upnp/control/basicevent1`.

### HTTP Headers
```http
POST /upnp/control/basicevent1 HTTP/1.1
Host: <ip>:<port>
Content-Type: text/xml; charset="utf-8"
SOAPACTION: "urn:Belkin:service:basicevent:1#<ActionName>"
Content-Length: <bytes>
Connection: close
```

### 4.1 `GetBinaryState`
**Request Body**:
```xml
<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:GetBinaryState xmlns:u="urn:Belkin:service:basicevent:1"></u:GetBinaryState>
  </s:Body>
</s:Envelope>
```

**Response Body (Switch / Mini Plug)**:
```xml
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:GetBinaryStateResponse xmlns:u="urn:Belkin:service:basicevent:1">
      <BinaryState>1</BinaryState>
    </u:GetBinaryStateResponse>
  </s:Body>
</s:Envelope>
```
*(Note: Some firmware returns pipe-delimited telemetry in `<BinaryState>`, e.g. `1|1490000000|...`—always parse `binaryState.trim().split("|").first()` where `1` or `8` means ON and `0` means OFF.)*

**Response Body (WeMo Dimmer)**:
```xml
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:GetBinaryStateResponse xmlns:u="urn:Belkin:service:basicevent:1">
      <BinaryState>1</BinaryState>
      <brightness>78</brightness>
      <fader>300:-1:1:0:78</fader>
    </u:GetBinaryStateResponse>
  </s:Body>
</s:Envelope>
```

### 4.2 `SetBinaryState`
**Request Body (Switch / Mini Plug ON/OFF)**:
```xml
<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:SetBinaryState xmlns:u="urn:Belkin:service:basicevent:1">
      <BinaryState>1</BinaryState>
    </u:SetBinaryState>
  </s:Body>
</s:Envelope>
```

**Request Body (WeMo Dimmer Brightness + State)**:
For WeMo Dimmer (`IN_WALL_DIMMER`), include `<brightness>` (1–100) alongside `<BinaryState>` when turning on or adjusting brightness:
```xml
<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:SetBinaryState xmlns:u="urn:Belkin:service:basicevent:1">
      <BinaryState>1</BinaryState>
      <brightness>75</brightness>
    </u:SetBinaryState>
  </s:Body>
</s:Envelope>
```
*(Note: In `GetBinaryStateResponse` / `SetBinaryStateResponse`, if the device is currently transitioning or returns `<BinaryState>Error</BinaryState>`, check HTTP 200 status and re-query or inspect `<brightness>`.)*

### 4.3 `GetSignalStrength` (Optional Telemetry)
WeMo devices also support `SOAPACTION: "urn:Belkin:service:basicevent:1#GetSignalStrength"` at `/upnp/control/basicevent1`, which returns `<SignalStrength>` (0–100%).
