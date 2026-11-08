package com.example.network

import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure OpenSSL `EVP_BytesToKey` MD5 + `AES-128-CBC` Wi-Fi password encryption and decryption
 * matching `pywemo/ouimeaux_device/__init__.py` and `pywemo/ouimeaux_device/api/wifi_setup.py`:
 * - **Method 1** (Gen-1 OpenWrt `F7C030` / `F7C059` / `F7C063`): `mac[0..6] + serial + mac[6..12]`, `addLengths = true`
 * - **Method 2** (Gen-2 RTOS `WLS040` / `WLS0403` / `WDS060` / `WSP080`): `mac[0..6] + serial + mac[6..12] + METHOD_2_RTOS_SALT_SUFFIX`, `addLengths = false`
 * - **Method 3** (`binaryOption = 1`): `mac[0..3] + mac[9..12] + serial + METHOD_3_BINARY_OPTION_EXTRA + mac[6..9] + mac[3..6]`, `addLengths = true`
 */
object WeMoPasswordCrypto {

    const val METHOD_2_RTOS_SALT_SUFFIX = "b3{8t;80dIN{ra83eC1s?M70?683@2Yf"
    const val METHOD_3_BINARY_OPTION_EXTRA = "bVduaWFyZkllaWVAb3RjbHAkcm9uT2Jh"

    fun buildKeyData(
        macAddress: String,
        serialNumber: String,
        encryptionMethod: Int = 1
    ): String {
        val cleanMac = macAddress.replace(":", "").replace("-", "").trim()
            .padEnd(12, '0').take(12)
        val cleanSerial = serialNumber.trim()
        return when (encryptionMethod) {
            2 -> cleanMac.substring(0, 6) + cleanSerial + cleanMac.substring(6, 12) + METHOD_2_RTOS_SALT_SUFFIX
            3 -> cleanMac.substring(0, 3) + cleanMac.substring(9, 12) + cleanSerial +
                METHOD_3_BINARY_OPTION_EXTRA + cleanMac.substring(6, 9) + cleanMac.substring(3, 6)
            else -> cleanMac.substring(0, 6) + cleanSerial + cleanMac.substring(6, 12)
        }
    }

    fun encryptWifiPassword(
        plainPassword: String,
        macAddress: String,
        serialNumber: String,
        encryptionMethod: Int = 1,
        addPasswordLengths: Boolean? = null
    ): String {
        val keydata = buildKeyData(macAddress, serialNumber, encryptionMethod).padEnd(16, '0')
        val keyBytes = keydata.toByteArray(Charsets.UTF_8)
        val saltBytes = keydata.substring(0, 8).toByteArray(Charsets.UTF_8)
        val ivBytes = keydata.substring(0, 16).toByteArray(Charsets.UTF_8)

        val md5 = MessageDigest.getInstance("MD5")
        md5.update(keyBytes)
        md5.update(saltBytes)
        val derivedKey = md5.digest().copyOfRange(0, 16)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derivedKey, "AES"), IvParameterSpec(ivBytes))
        val plainBytes = plainPassword.toByteArray(Charsets.UTF_8)
        val encryptedBytes = cipher.doFinal(plainBytes)
        val base64Encoded = Base64.getEncoder().encodeToString(encryptedBytes)

        val shouldAddLengths = addPasswordLengths ?: (encryptionMethod != 2)
        return if (shouldAddLengths) {
            val encLenHex = String.format(Locale.US, "%02x", base64Encoded.length and 0xFF)
            val origLenHex = String.format(Locale.US, "%02x", plainBytes.size and 0xFF)
            base64Encoded + encLenHex + origLenHex
        } else {
            base64Encoded
        }
    }

    fun decryptWifiPassword(
        encryptedPayload: String,
        macAddress: String,
        serialNumber: String,
        encryptionMethod: Int = 1,
        hasLengthSuffix: Boolean = (encryptionMethod != 2)
    ): String {
        val keydata = buildKeyData(macAddress, serialNumber, encryptionMethod).padEnd(16, '0')
        val keyBytes = keydata.toByteArray(Charsets.UTF_8)
        val saltBytes = keydata.substring(0, 8).toByteArray(Charsets.UTF_8)
        val ivBytes = keydata.substring(0, 16).toByteArray(Charsets.UTF_8)

        val md5 = MessageDigest.getInstance("MD5")
        md5.update(keyBytes)
        md5.update(saltBytes)
        val derivedKey = md5.digest().copyOfRange(0, 16)

        val base64Part = if (hasLengthSuffix && encryptedPayload.length > 4) {
            encryptedPayload.substring(0, encryptedPayload.length - 4)
        } else {
            encryptedPayload
        }
        val cipherBytes = Base64.getDecoder().decode(base64Part)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derivedKey, "AES"), IvParameterSpec(ivBytes))
        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }
}
