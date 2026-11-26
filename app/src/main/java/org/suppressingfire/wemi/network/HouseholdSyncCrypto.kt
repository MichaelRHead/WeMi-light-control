package org.suppressingfire.wemi.network

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

/**
 * Authenticated encryption helper for ephemeral local-LAN household pairing transfers.
 *
 * Derives a 256-bit AES key from the one-time 6-digit numeric pairing code and a random 16-byte
 * per-push salt using `PBKDF2WithHmacSHA256` (100,000 iterations), then encrypts the UTF-8
 * household JSON payload with `AES/GCM/NoPadding` (12-byte random IV, 128-bit auth tag, fixed
 * domain AAD). All temporary PIN char arrays and key byte arrays are zeroed in `finally` blocks.
 */
object HouseholdSyncCrypto {
    private const val ENVELOPE_VERSION = 1
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val PIN_LENGTH = 6
    private const val MAX_ENVELOPE_CHARS = 384 * 1024
    private const val AAD_DOMAIN = "WeMi-HouseholdSync-v1"
    private val secureRandom = SecureRandom()

    /** Generates a cryptographically random 6-digit numeric pairing code (`000000`..`999999`). */
    fun generatePairingCode(): String =
        secureRandom.nextInt(1_000_000).toString().padStart(PIN_LENGTH, '0')

    /** Formats a 6-digit pairing code for 10-foot TV / phone display (`123-456`). */
    fun formatPairingCode(rawCode: String): String {
        val digits = normalizePairingCode(rawCode)
        return if (digits.length == PIN_LENGTH) "${digits.substring(0, 3)}-${digits.substring(3)}" else digits
    }

    /** Strips dashes/spaces so users can type `123456` or `123-456`. */
    fun normalizePairingCode(rawCode: String): String =
        rawCode.filter { it in '0'..'9' }

    fun isValidPairingCode(rawCode: String): Boolean =
        normalizePairingCode(rawCode).length == PIN_LENGTH

    /**
     * Encrypts [plaintextJson] using [pairingCode] and wraps it in a compact JSON wire envelope
     * carrying version, AAD-authenticated sender label, random salt, random IV, and ciphertext.
     */
    fun encryptEnvelope(
        plaintextJson: String,
        pairingCode: String,
        senderLabel: String = "WeMi Device"
    ): String {
        val normalizedPin = normalizePairingCode(pairingCode)
        require(normalizedPin.length == PIN_LENGTH) { "Pairing code must be 6 digits" }
        val plainBytes = plaintextJson.toByteArray(Charsets.UTF_8)
        require(plainBytes.size <= HouseholdSyncCodec.MAX_JSON_BYTES) {
            "Household payload exceeds ${HouseholdSyncCodec.MAX_JSON_BYTES / 1024} KiB limit"
        }

        val safeSender = HouseholdSyncCodec.sanitizeLabel(senderLabel, maxLen = 64).ifEmpty { "WeMi Device" }
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
        val pinChars = normalizedPin.toCharArray()
        var keyBytes: ByteArray? = null
        return try {
            keyBytes = deriveAesKey(pinChars, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(buildV1Aad(ENVELOPE_VERSION, safeSender, salt, iv))
            val ciphertext = cipher.doFinal(plainBytes)
            buildString(ciphertext.size * 2) {
                append("{\"v\":").append(ENVELOPE_VERSION)
                append(",\"sender\":").append(HouseholdSyncCodec.quoteJson(safeSender))
                append(",\"salt\":").append(HouseholdSyncCodec.quoteJson(base64Encode(salt)))
                append(",\"iv\":").append(HouseholdSyncCodec.quoteJson(base64Encode(iv)))
                append(",\"ct\":").append(HouseholdSyncCodec.quoteJson(base64Encode(ciphertext)))
                append('}')
            }
        } finally {
            Arrays.fill(pinChars, '\u0000')
            keyBytes?.let { Arrays.fill(it, 0.toByte()) }
            Arrays.fill(plainBytes, 0.toByte())
        }
    }

    /**
     * Authenticates and decrypts an encrypted wire envelope produced by [encryptEnvelope].
     *
     * Throws [SecurityException] if the pairing code is wrong or the ciphertext/sender/AAD was
     * tampered with, and [IllegalArgumentException] if the envelope structure is malformed.
     */
    fun decryptEnvelope(
        envelopeJson: String,
        pairingCode: String
    ): Pair<String, String> {
        val normalizedPin = normalizePairingCode(pairingCode)
        require(normalizedPin.length == PIN_LENGTH) { "Pairing code must be 6 digits" }
        require(envelopeJson.length <= MAX_ENVELOPE_CHARS) { "Sync envelope exceeds safety limit" }

        val obj = HouseholdSyncCodec.parseJsonObject(envelopeJson)
        val version = (obj["v"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("Missing envelope version")
        require(version == ENVELOPE_VERSION) { "Unsupported sync envelope version $version" }

        val sender = HouseholdSyncCodec.sanitizeLabel(obj["sender"] as? String, maxLen = 64)
            .ifEmpty { "WeMi Device" }
        val salt = base64Decode(obj["salt"] as? String ?: "")
        val iv = base64Decode(obj["iv"] as? String ?: "")
        val ctRaw = (obj["ct"] as? String)?.trim().orEmpty()
        require(ctRaw.length <= ((HouseholdSyncCodec.MAX_JSON_BYTES + 16) * 4) / 3 + 16) {
            "Ciphertext exceeds safety limit"
        }
        val ciphertext = base64Decode(ctRaw)
        require(salt.size == SALT_BYTES) { "Invalid salt length" }
        require(iv.size == IV_BYTES) { "Invalid IV length" }
        require(ciphertext.size in 16..(HouseholdSyncCodec.MAX_JSON_BYTES + 16)) { "Invalid ciphertext length" }

        val pinChars = normalizedPin.toCharArray()
        var keyBytes: ByteArray? = null
        var plainBytes: ByteArray? = null
        return try {
            keyBytes = deriveAesKey(pinChars, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(buildV1Aad(version, sender, salt, iv))
            plainBytes = cipher.doFinal(ciphertext)
            require(plainBytes.size <= HouseholdSyncCodec.MAX_JSON_BYTES) {
                "Decrypted household payload exceeds safety limit"
            }
            sender to String(plainBytes, Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw SecurityException("Invalid pairing code or corrupted transfer", e)
        } finally {
            Arrays.fill(pinChars, '\u0000')
            keyBytes?.let { Arrays.fill(it, 0.toByte()) }
            plainBytes?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    private fun buildV1Aad(version: Int, sender: String, salt: ByteArray, iv: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(160)
        fun writeField(bytes: ByteArray) {
            out.write(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            out.write(bytes)
        }
        writeField("$AAD_DOMAIN:$version".toByteArray(Charsets.US_ASCII))
        writeField(sender.toByteArray(Charsets.UTF_8))
        writeField(salt)
        writeField(iv)
        return out.toByteArray()
    }

    private fun deriveAesKey(pinChars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pinChars, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun base64Encode(bytes: ByteArray): String =
        Base64.Default.encode(bytes)

    private fun base64Decode(encoded: String): ByteArray =
        try {
            Base64.Default.decode(encoded.trim())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Malformed Base64 in sync envelope", e)
        }
}
