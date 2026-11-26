package org.suppressingfire.wemi

import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.suppressingfire.wemi.network.HardwareRulesDbCodec
import org.suppressingfire.wemi.network.HardwareRulesSafetyValidator

/**
 * Loopback UPnP/SOAP/HTTP simulator focused on Belkin `rules1`, `/rulesservice.xml`, binary
 * `/rules.db` SQLite-in-ZIP archives, and `timesync1` (plus basic `basicevent1` so full-stack
 * Compose E2E tests can adopt and inspect simulated OpenWrt and RTOS switches).
 */
class FakeHardwareRulesServer(
    initialFriendlyName: String = "Porch Light Switch",
    val deviceTypeUrn: String = "urn:Belkin:device:lightswitch:1",
    val modelName: String = "WLS0403",
    val serialNumber: String = "WLS0403RULES01",
    val firmwareVersion: String = "WeMo_WW_2.00.11573.PVT-OWRT-LIGHTV2",
    val udn: String = "uuid:Lightswitch-3_0-WLS0403RULES01",
    val macAddress: String = "EC1A59BB2233",
    val isRtos: Boolean = false,
    val supportsRulesDb: Boolean = true,
    val setupXmlIncludesRulesService: Boolean = true,
    initialDbVersion: Int = 3,
    initialRulesZipBytes: ByteArray? = null,
    initialDeviceTimeUtc: Long = 1_700_000_000L
) : AutoCloseable {

    val friendlyName = AtomicReference(initialFriendlyName)
    val relayStateOn = AtomicBoolean(false)
    val currentDbVersion = AtomicInteger(initialDbVersion)
    val currentRulesZipBytes = AtomicReference<ByteArray?>(initialRulesZipBytes)
    val deviceTimeUtc = AtomicLong(initialDeviceTimeUtc)
    val rejectNextStoreRules = AtomicBoolean(false)
    val simulateReadBackStaleVersion = AtomicBoolean(false)

    val scpdFetches = AtomicInteger(0)
    val fetchRulesCalls = AtomicInteger(0)
    val storeRulesCalls = AtomicInteger(0)
    val rulesDbDownloads = AtomicInteger(0)
    val getDeviceTimeCalls = AtomicInteger(0)
    val timeSyncCalls = AtomicInteger(0)
    val lastTimeSyncTimezone = AtomicReference("")
    val receivedPaths = CopyOnWriteArrayList<String>()

    private val running = AtomicBoolean(true)
    private val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val port: Int = serverSocket.localPort

    private val serverThread = thread(start = true, isDaemon = true, name = "FakeRulesServer-$port") {
        while (running.get() && !serverSocket.isClosed) {
            try {
                serverSocket.accept().use { sock -> handleConnection(sock) }
            } catch (_: Exception) {
                if (!running.get()) break
            }
        }
    }

    private fun handleConnection(sock: Socket) {
        sock.soTimeout = 2000
        val input = BufferedInputStream(sock.getInputStream())
        val requestLine = readHttpLine(input) ?: return
        var contentLength = 0
        var soapAction = ""
        while (true) {
            val header = readHttpLine(input) ?: break
            if (header.isEmpty()) break
            when {
                header.startsWith("Content-Length:", ignoreCase = true) ->
                    contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                header.startsWith("SOAPACTION:", ignoreCase = true) ->
                    soapAction = header.substringAfter(":").trim()
            }
        }
        val bodyBytes = ByteArray(contentLength.coerceAtLeast(0))
        var readTotal = 0
        while (readTotal < bodyBytes.size) {
            val n = input.read(bodyBytes, readTotal, bodyBytes.size - readTotal)
            if (n == -1) break
            readTotal += n
        }
        val reqBody = String(bodyBytes, 0, readTotal, Charsets.UTF_8)
        val path = requestLine.split(" ").getOrNull(1).orEmpty()
        receivedPaths.add(path)

        val out = sock.getOutputStream()
        if (requestLine.startsWith("GET /rules.db")) {
            rulesDbDownloads.incrementAndGet()
            val zip = currentRulesZipBytes.get()
            if (zip == null) {
                writeResponse(out, "404 Not Found", "text/plain", "Not Found".toByteArray())
            } else {
                writeResponse(out, "200 OK", "application/octet-stream", zip)
            }
            return
        }

        var status = "200 OK"
        val xml = when {
            requestLine.startsWith("GET /setup.xml") -> buildSetupXml()
            requestLine.startsWith("GET /rulesservice.xml") -> {
                scpdFetches.incrementAndGet()
                buildRulesScpdXml()
            }
            soapAction.contains("FetchRules") -> {
                fetchRulesCalls.incrementAndGet()
                if (!supportsRulesDb) {
                    status = "500 Internal Server Error"
                    """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><errorCode>401</errorCode><errorDescription>Invalid Action</errorDescription></detail></s:Fault></s:Body></s:Envelope>"""
                } else {
                    val ver = currentDbVersion.get()
                    """
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                      <s:Body>
                        <u:FetchRulesResponse xmlns:u="urn:Belkin:service:rules:1">
                          <ruleDbPath>http://127.0.0.1:$port/rules.db</ruleDbPath>
                          <ruleDbVersion>$ver</ruleDbVersion>
                          <errorInfo></errorInfo>
                        </u:FetchRulesResponse>
                      </s:Body>
                    </s:Envelope>
                    """.trimIndent()
                }
            }
            soapAction.contains("StoreRules") -> {
                storeRulesCalls.incrementAndGet()
                if (rejectNextStoreRules.getAndSet(false)) {
                    """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:StoreRulesResponse xmlns:u="urn:Belkin:service:rules:1"><errorInfo>DB write error</errorInfo></u:StoreRulesResponse></s:Body></s:Envelope>"""
                } else {
                    val newVer = Regex("<ruleDbVersion>(\\d+)</ruleDbVersion>").find(reqBody)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    val bodyRaw = Regex("<ruleDbBody>([\\s\\S]*?)</ruleDbBody>").find(reqBody)?.groupValues?.getOrNull(1).orEmpty()
                    val b64 = bodyRaw
                        .replace("&lt;![CDATA[", "")
                        .replace("]]&gt;", "")
                        .replace("<![CDATA[", "")
                        .replace("]]>", "")
                        .trim()
                    val isStale = simulateReadBackStaleVersion.getAndSet(false)
                    if (!isStale) {
                        if (b64.isNotEmpty()) {
                            runCatching { currentRulesZipBytes.set(Base64.decode(b64, Base64.DEFAULT)) }
                        }
                        if (newVer != null) {
                            currentDbVersion.set(newVer)
                        }
                    }
                    """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:StoreRulesResponse xmlns:u="urn:Belkin:service:rules:1"><errorInfo>Storing of rules DB Successful!</errorInfo></u:StoreRulesResponse></s:Body></s:Envelope>"""
                }
            }
            soapAction.contains("GetDeviceTime") -> {
                getDeviceTimeCalls.incrementAndGet()
                """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:GetDeviceTimeResponse xmlns:u="urn:Belkin:service:timesync:1"><DeviceTime>${deviceTimeUtc.get()}</DeviceTime></u:GetDeviceTimeResponse></s:Body></s:Envelope>"""
            }
            soapAction.contains("TimeSync") -> {
                timeSyncCalls.incrementAndGet()
                Regex("<UTC>(\\d+)</UTC>").find(reqBody)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { deviceTimeUtc.set(it) }
                Regex("<TimeZone>([^<]+)</TimeZone>").find(reqBody)?.groupValues?.getOrNull(1)?.let { lastTimeSyncTimezone.set(it) }
                """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:TimeSyncResponse xmlns:u="urn:Belkin:service:timesync:1"><Status>0</Status></u:TimeSyncResponse></s:Body></s:Envelope>"""
            }
            soapAction.contains("GetBinaryState") -> {
                val state = if (relayStateOn.get()) 1 else 0
                """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:GetBinaryStateResponse xmlns:u="urn:Belkin:service:basicevent:1"><BinaryState>$state</BinaryState></u:GetBinaryStateResponse></s:Body></s:Envelope>"""
            }
            soapAction.contains("ChangeFriendlyName") -> {
                Regex("<FriendlyName>([^<]+)</FriendlyName>").find(reqBody)?.groupValues?.getOrNull(1)?.trim()?.let { friendlyName.set(it) }
                """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:ChangeFriendlyNameResponse xmlns:u="urn:Belkin:service:basicevent:1"><PluginKey>0</PluginKey></u:ChangeFriendlyNameResponse></s:Body></s:Envelope>"""
            }
            soapAction.contains("GetSignalStrength") ->
                """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:GetSignalStrengthResponse xmlns:u="urn:Belkin:service:basicevent:1"><SignalStrength>92</SignalStrength></u:GetSignalStrengthResponse></s:Body></s:Envelope>"""
            soapAction.contains("GetMetaInfo") ->
                """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:GetMetaInfoResponse xmlns:u="urn:Belkin:service:metainfo:1"><MetaInfo>$macAddress|$serialNumber|$modelName|$firmwareVersion|HomeWiFi|$modelName</MetaInfo></u:GetMetaInfoResponse></s:Body></s:Envelope>"""
            else -> "<root/>"
        }
        writeResponse(out, status, "text/xml; charset=\"utf-8\"", xml.toByteArray(Charsets.UTF_8))
    }

    private fun buildSetupXml(): String {
        val rulesSvcXml = if (setupXmlIncludesRulesService) {
            """
            <service>
              <serviceType>urn:Belkin:service:rules:1</serviceType>
              <serviceId>urn:Belkin:serviceId:rules1</serviceId>
              <controlURL>/upnp/control/rules1</controlURL>
              <eventSubURL>/upnp/event/rules1</eventSubURL>
              <SCPDURL>/rulesservice.xml</SCPDURL>
            </service>
            """.trimIndent()
        } else {
            ""
        }
        return """
            <?xml version="1.0"?>
            <root xmlns="urn:Belkin:device-1-0">
              <device>
                <deviceType>$deviceTypeUrn</deviceType>
                <friendlyName>${friendlyName.get()}</friendlyName>
                <manufacturer>Belkin International Inc.</manufacturer>
                <modelName>$modelName</modelName>
                <serialNumber>$serialNumber</serialNumber>
                <firmwareVersion>$firmwareVersion</firmwareVersion>
                <UDN>$udn</UDN>
                <macAddress>$macAddress</macAddress>
                <rtos>${if (isRtos) 1 else 0}</rtos>
                <binaryState>${if (relayStateOn.get()) 1 else 0}</binaryState>
                <serviceList>
                  <service>
                    <serviceType>urn:Belkin:service:basicevent:1</serviceType>
                    <controlURL>/upnp/control/basicevent1</controlURL>
                    <SCPDURL>/eventservice.xml</SCPDURL>
                  </service>
                  <service>
                    <serviceType>urn:Belkin:service:timesync:1</serviceType>
                    <controlURL>/upnp/control/timesync1</controlURL>
                    <SCPDURL>/timesyncservice.xml</SCPDURL>
                  </service>
                  $rulesSvcXml
                </serviceList>
              </device>
            </root>
        """.trimIndent()
    }

    private fun buildRulesScpdXml(): String = if (supportsRulesDb) {
        """
        <scpd xmlns="urn:Belkin:service-1-0">
          <actionList>
            <action><name>UpdateWeeklycalendar</name></action>
            <action><name>FetchRules</name></action>
            <action><name>StoreRules</name></action>
            <action><name>GetRulesDBPath</name></action>
          </actionList>
        </scpd>
        """.trimIndent()
    } else {
        """
        <scpd xmlns="urn:Belkin:service-1-0">
          <actionList>
            <action><name>GetRulesDBPath</name></action>
            <action><name>StoreRules</name></action>
          </actionList>
        </scpd>
        """.trimIndent()
    }

    private fun writeResponse(out: java.io.OutputStream, status: String, contentType: String, body: ByteArray) {
        val header = "HTTP/1.1 $status\r\nContent-Type: $contentType\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    private fun readHttpLine(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream(128)
        while (out.size() < 8192) {
            val b = input.read()
            if (b == -1) return if (out.size() == 0) null else out.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
            if (b == '\n'.code) return out.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
            out.write(b)
        }
        return out.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket.close() }
        serverThread.join(500)
    }

    companion object {
        fun buildSampleOpenWrtRulesZip(
            ownerUdn: String,
            targetUdn: String,
            workDir: File,
            zipEntryName: String = HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME
        ): ByteArray {
            val dbFile = File.createTempFile("sample_rules_", ".db", workDir.apply { mkdirs() })
            return try {
                HardwareRulesSafetyValidator.createEmptyBelkinSchemaDb(dbFile)
                val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                try {
                    db.execSQL("INSERT INTO RULES VALUES (1, 'Hold to Toggle Porch', 'Long Press', 0, '12201982', '12201982', '1', 0)")
                    db.execSQL("INSERT INTO RULEDEVICES (RuleID, DeviceID, GroupID, DayID, StartTime, RuleDuration, StartAction, EndAction, SensorDuration, Type, Value, Level, OnModeOffset, OffModeOffset, CountdownTime, EndTime) VALUES (1, '$ownerUdn', 0, -1, 60, 86340, 2.0, -1.0, -1, -1, -1, -1, 0, 0, -1, 86400)")
                    db.execSQL("INSERT INTO TARGETDEVICES (RuleID, DeviceID, DeviceIndex) VALUES (1, '$targetUdn', 0)")

                    db.execSQL("INSERT INTO RULES VALUES (2, 'Auto-Off Bathroom Fan', 'Countdown Rule', 1, '12201982', '12201982', '1', 0)")
                    for (day in 1..7) {
                        db.execSQL("INSERT INTO RULEDEVICES (RuleID, DeviceID, GroupID, DayID, StartTime, RuleDuration, StartAction, EndAction, SensorDuration, Type, Value, Level, OnModeOffset, OffModeOffset, CountdownTime, EndTime) VALUES (2, '$ownerUdn', 0, $day, 0, 86400, 1.0, 0.0, -1, -1, -1, -1, 0, 0, 1800, 86400)")
                    }

                    db.execSQL("INSERT INTO RULES VALUES (3, 'Evening Sunset Porch', 'Time Interval', 2, '12201982', '12201982', '1', 0)")
                    for (day in 2..6) {
                        db.execSQL("INSERT INTO RULEDEVICES (RuleID, DeviceID, GroupID, DayID, StartTime, RuleDuration, StartAction, EndAction, SensorDuration, Type, Value, Level, OnModeOffset, OffModeOffset, CountdownTime, EndTime) VALUES (3, '$ownerUdn', 0, $day, 64802, 14400, 1.0, 0.0, -1, -1, -1, -1, -900, 0, -1, 79200)")
                    }

                    db.execSQL("INSERT INTO RULES VALUES (4, 'Vacation Randomizer', 'Away Mode', 3, '12201982', '12201982', '0', 0)")
                    db.execSQL("INSERT INTO RULEDEVICES (RuleID, DeviceID, GroupID, DayID, StartTime, RuleDuration, StartAction, EndAction, SensorDuration, Type, Value, Level, OnModeOffset, OffModeOffset, CountdownTime, EndTime) VALUES (4, '$ownerUdn', 0, 1, 72000, 10800, 1.0, 0.0, -1, -1, -1, -1, 0, 0, -1, 82800)")

                    db.execSQL("INSERT INTO LOCATIONINFO (cityName, countryName, latitude, longitude, countryCode, region) VALUES ('Mountain View', 'United States', '37.3861', '-122.0839', 'US', 'CA')")
                } finally {
                    db.close()
                }
                HardwareRulesDbCodec.packFileToZipBytes(dbFile, zipEntryName)
            } finally {
                runCatching { dbFile.delete() }
            }
        }
    }
}
