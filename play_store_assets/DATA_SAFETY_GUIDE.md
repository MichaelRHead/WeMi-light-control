# Google Play Console Upload & Data Safety Checklist

## 1. Graphic Assets Ready to Upload (`/play_store_assets/`)

| Asset Type | Google Play Spec | File Path in Repository | Verified Resolution |
| :--- | :--- | :--- | :--- |
| **App Icon** | 512 × 512 px, 32-bit PNG | `play_store_assets/play_store_icon_512x512.png` | `512 × 512` |
| **Feature Graphic** | 1024 × 500 px, PNG | `play_store_assets/feature_graphic_1024x500.png` | `1024 × 500` |
| **Phone Screenshot 1** | 1080 × 1920 px (9:16 PNG) | `play_store_assets/screenshots/phone/phone_1_devices_and_power_rings.png` | `1080 × 1920` |
| **Phone Screenshot 2** | 1080 × 1920 px (9:16 PNG) | `play_store_assets/screenshots/phone/phone_2_ssdp_network_scanner.png` | `1080 × 1920` |
| **Phone Screenshot 3** | 1080 × 1920 px (9:16 PNG) | `play_store_assets/screenshots/phone/phone_3_inline_dimmer_control.png` | `1080 × 1920` |
| **Phone Screenshot 4** | 1080 × 1920 px (9:16 PNG) | `play_store_assets/screenshots/phone/phone_4_local_rules_and_upnp_log.png` | `1080 × 1920` |
| **7" Tablet Screenshot 1** | 1200 × 1920 px (PNG) | `play_store_assets/screenshots/tablet_7_inch/tablet7_1_devices_dashboard.png` | `1200 × 1920` |
| **7" Tablet Screenshot 2** | 1200 × 1920 px (PNG) | `play_store_assets/screenshots/tablet_7_inch/tablet7_2_ssdp_and_rules.png` | `1200 × 1920` |
| **10" Tablet Screenshot 1** | 1600 × 2560 px (PNG) | `play_store_assets/screenshots/tablet_10_inch/tablet10_1_devices_dashboard.png` | `1600 × 2560` |
| **10" Tablet Screenshot 2** | 1600 × 2560 px (PNG) | `play_store_assets/screenshots/tablet_10_inch/tablet10_2_ssdp_and_rules.png` | `1600 × 2560` |

---

## 2. Google Play Console Questionnaire Answers

### A. Data Safety Section
- **Does your app collect or share any of the required user data types?** → **No**
- **Explanation:** All switch IPs, friendly names, SSDP discovery results, and rules are stored strictly on-device in local Room SQLite storage and communicated only over the user's local LAN (`239.255.255.250:1900` and `http://<local-ip>:49153`).

### B. App Content & Permissions
- **Ads:** No, my app does not contain ads.
- **App Access:** All functionality is available without special access or login credentials (pre-populated with demo/default devices and includes a live local SSDP scanner).
- **Target Audience:** 13+ / Everyone (General utility app).
- **Permissions Used (Install-time normal permissions only):**
  - `android.permission.INTERNET` (Local HTTP SOAP calls to switches)
  - `android.permission.ACCESS_NETWORK_STATE`
  - `android.permission.ACCESS_WIFI_STATE`
  - `android.permission.CHANGE_WIFI_MULTICAST_STATE` (Acquires `WifiManager.MulticastLock` during SSDP `M-SEARCH` discovery)
