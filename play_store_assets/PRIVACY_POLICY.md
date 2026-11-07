# Privacy Policy for WeMi Switch Control

**Effective Date:** November 7, 2026

## Overview
WeMi Switch Control ("the App") is a local-network smart switch controller. We respect your privacy and have designed the App so that all device configurations, switch states, and automation rules remain strictly on your Android device.

## Information Collection and Use
- **No Personal Data Collected:** The App does not require an account, login, email address, or cloud registration.
- **Local Network Communication Only:** The App uses standard Android Wi-Fi and network permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`) solely to discover UPnP/SSDP smart switches on your local Wi-Fi network (`239.255.255.250:1900`) and send local HTTP SOAP commands (`GetBinaryState`, `SetBinaryState`) directly to your switches' local IP addresses.
- **On-Device Storage:** Device names, local IP addresses, ports, and schedules are stored locally on your device using Android Room SQLite storage.

## Third-Party Sharing & Analytics
The App contains no advertising SDKs, no third-party analytics trackers, and does not sell or transmit user data to any external server.

## Contact
For questions regarding this Privacy Policy or local UPnP switch compatibility, please contact the developer via the support email listed on the Google Play Store page.
