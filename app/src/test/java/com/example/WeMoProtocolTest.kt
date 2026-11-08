package com.example

/**
 * Protocol & Hardware Loopback Test Suite Index:
 *
 * - [FakeWeMoUpnpServer] — Reusable loopback Belkin WeMo HTTP / UPnP / SOAP / GENA / `WiFiSetup1` server (`FakeWeMoUpnpServer.kt`)
 * - [WeMoSkuAndCryptoTest] — Hardware SKU classification, `/setup.xml` parsing, semantic device categorization, and `pywemo` AES-128-CBC encryption (`WeMoSkuAndCryptoTest.kt`)
 * - [WeMoSoapAndSelfHealingTest] — 9-port UPnP probing, `SetBinaryState`/`GetBinaryState`, `<BinaryState>Error</BinaryState>` quirk, offline rollback, and multi-device startup probing (`WeMoSoapAndSelfHealingTest.kt`)
 * - [WeMoGenaProvisioningAndRulesTest] — UPnP GENA `SUBSCRIBE`/HTTP 412/embedded `NOTIFY` server, `WeMoRuleScheduler`, and `10.22.22.1` `WiFiSetup1` zero-cloud provisioning (`WeMoGenaProvisioningAndRulesTest.kt`)
 */
