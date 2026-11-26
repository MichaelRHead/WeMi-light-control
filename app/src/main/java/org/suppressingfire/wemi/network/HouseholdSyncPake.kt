package org.suppressingfire.wemi.network

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Pure-Kotlin, zero-dependency balanced PAKE (**SPAKE2**, RFC 9382) over the RFC 3526 2048-bit
 * MODP safe-prime group (Group 14: $p = 2q + 1$, generator $g = 2$ of the prime-order subgroup
 * $\mathbb{G}_q$) for local-LAN Household Config Sync pairing (`v = 2`).
 *
 * Unlike raw PBKDF2-over-HTTP, SPAKE2 binds a fresh ephemeral Diffie-Hellman exchange to the
 * 6-digit pairing code so a passive LAN eavesdropper or active MITM cannot offline brute-force the
 * 6-digit PIN from captured packets (`~10^6` space). Every wrong attempt requires an online
 * handshake against the receiver's strict 5-attempt lockout and single-use challenge nonce.
 */
@OptIn(ExperimentalEncodingApi::class)
internal object HouseholdSyncPake {
    const val PROTOCOL_VERSION = 2
    private const val PBKDF2_ITERATIONS = 100_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val EPHEMERAL_EXPONENT_BITS = 256
    private const val MODULUS_BYTES = 256
    private const val MAX_ENVELOPE_CHARS = 384 * 1024
    private const val KEY_DERIVATION_CONTEXT = "WeMi-SPAKE2-v2"
    private const val AAD_DOMAIN = "WeMi-HouseholdSync-v2"

    // RFC 3526 Section 3 — 2048-bit MODP Group (ID = 14), safe prime p = 2q + 1, p ≡ 7 (mod 8)
    internal val P: BigInteger = BigInteger(
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
            "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
            "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
            "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
            "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
            "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
            "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
            "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
            "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
            "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
            "15728E5A8AACAA68FFFFFFFFFFFFFFFF",
        16
    )
    internal val Q: BigInteger = P.subtract(BigInteger.ONE).shiftRight(1)
    internal val G: BigInteger = BigInteger.valueOf(2L)
    private val TWO: BigInteger = BigInteger.valueOf(2L)
    private val P_MINUS_TWO: BigInteger = P.subtract(TWO)
    private val P_MINUS_THREE: BigInteger = P.subtract(BigInteger.valueOf(3L))
    private val Q_MINUS_ONE: BigInteger = Q.subtract(BigInteger.ONE)

    // Nothing-up-my-sleeve subgroup generators M, N ∈ G_q \ {1} with unknown discrete logs
    internal val M: BigInteger = deriveSubgroupGenerator("WeMi-SPAKE2-Group14-M-v2")
    internal val N: BigInteger = deriveSubgroupGenerator("WeMi-SPAKE2-Group14-N-v2")

    private val secureRandom = SecureRandom()

    data class ServerChallenge(
        val nonceB64: String,
        val nonceBytes: ByteArray,
        val responseJson: String,
        val yScalar: BigInteger,
        val yPublicBytes: ByteArray,
        val mInvW: BigInteger
    )

    /**
     * Generates a single-use receiver SPAKE2 challenge (`Y = g^y * N^w mod p`) bound to a fresh
     * 16-byte nonce and 16-byte PBKDF2 salt for `POST /wemi-sync/v1/hello`.
     */
    fun createServerChallenge(pairingCode: String): ServerChallenge {
        val normalizedPin = HouseholdSyncCrypto.normalizePairingCode(pairingCode)
        require(normalizedPin.length == 6) { "Pairing code must be 6 digits" }
        val nonce = ByteArray(NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val w = derivePasswordScalar(normalizedPin, salt)
        val yScalar = randomScalar()
        val yPublic = G.modPow(yScalar, P).multiply(N.modPow(w, P)).mod(P)
        val yPublicBytes = toFixedUnsignedBytes(yPublic)
        val mInvW = M.modPow(w, P).modInverse(P)
        val nonceB64 = Base64.Default.encode(nonce)
        val responseJson = buildString(512) {
            append("{\"v\":").append(PROTOCOL_VERSION)
            append(",\"nonce\":").append(HouseholdSyncCodec.quoteJson(nonceB64))
            append(",\"salt\":").append(HouseholdSyncCodec.quoteJson(Base64.Default.encode(salt)))
            append(",\"y\":").append(HouseholdSyncCodec.quoteJson(Base64.Default.encode(yPublicBytes)))
            append('}')
        }
        return ServerChallenge(
            nonceB64 = nonceB64,
            nonceBytes = nonce,
            responseJson = responseJson,
            yScalar = yScalar,
            yPublicBytes = yPublicBytes,
            mInvW = mInvW
        )
    }

    /**
     * Completes the sender side of the SPAKE2 handshake against [helloResponseJson] and encrypts
     * [plaintextJson] under the transcript-bound AES-256-GCM key (`v = 2`).
     */
    fun encryptPushV2(
        plaintextJson: String,
        pairingCode: String,
        senderLabel: String,
        helloResponseJson: String
    ): String {
        val normalizedPin = HouseholdSyncCrypto.normalizePairingCode(pairingCode)
        require(normalizedPin.length == 6) { "Pairing code must be 6 digits" }
        require(helloResponseJson.length <= 8192) { "Handshake response exceeds safety limit" }
        val plainBytes = plaintextJson.toByteArray(Charsets.UTF_8)
        require(plainBytes.size <= HouseholdSyncCodec.MAX_JSON_BYTES) {
            "Household payload exceeds ${HouseholdSyncCodec.MAX_JSON_BYTES / 1024} KiB limit"
        }

        val helloObj = HouseholdSyncCodec.parseJsonObject(helloResponseJson)
        val version = (helloObj["v"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("Missing handshake version")
        require(version == PROTOCOL_VERSION) { "Unsupported sync handshake version $version" }
        val nonce = decodeBase64(helloObj["nonce"] as? String ?: "")
        val salt = decodeBase64(helloObj["salt"] as? String ?: "")
        val rawY = decodeBase64(helloObj["y"] as? String ?: "")
        require(nonce.size == NONCE_BYTES) { "Invalid handshake nonce" }
        require(salt.size == SALT_BYTES) { "Invalid handshake salt" }
        require(rawY.size in 1..MODULUS_BYTES) { "Invalid receiver public element length" }

        val yPublic = BigInteger(1, rawY)
        require(isValidSubgroupElement(yPublic)) { "Invalid receiver SPAKE2 subgroup element" }
        val yCanonical = toFixedUnsignedBytes(yPublic)

        val w = derivePasswordScalar(normalizedPin, salt)
        val xScalar = randomScalar()
        val xPublic = G.modPow(xScalar, P).multiply(M.modPow(w, P)).mod(P)
        val xCanonical = toFixedUnsignedBytes(xPublic)
        val nInvW = N.modPow(w, P).modInverse(P)
        val unblindedY = yPublic.multiply(nInvW).mod(P)
        val sharedK = unblindedY.modPow(xScalar, P)
        val kBytes = toFixedUnsignedBytes(sharedK)

        val safeSender = HouseholdSyncCodec.sanitizeLabel(senderLabel, maxLen = 64).ifEmpty { "WeMi Device" }
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
        var keyBytes: ByteArray? = null
        return try {
            keyBytes = deriveTranscriptAesKey(nonce, xCanonical, yCanonical, kBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(buildV2Aad(nonce, xCanonical, yCanonical, safeSender, iv))
            val ciphertext = cipher.doFinal(plainBytes)
            buildString(ciphertext.size * 2 + 512) {
                append("{\"v\":").append(PROTOCOL_VERSION)
                append(",\"nonce\":").append(HouseholdSyncCodec.quoteJson(Base64.Default.encode(nonce)))
                append(",\"x\":").append(HouseholdSyncCodec.quoteJson(Base64.Default.encode(xCanonical)))
                append(",\"sender\":").append(HouseholdSyncCodec.quoteJson(safeSender))
                append(",\"iv\":").append(HouseholdSyncCodec.quoteJson(Base64.Default.encode(iv)))
                append(",\"ct\":").append(HouseholdSyncCodec.quoteJson(Base64.Default.encode(ciphertext)))
                append('}')
            }
        } finally {
            Arrays.fill(plainBytes, 0.toByte())
            Arrays.fill(kBytes, 0.toByte())
            keyBytes?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    /** Extracts the single-use handshake nonce from a `v = 2` push envelope before burning it. */
    fun parsePushEnvelopeV2(envelopeJson: String): ParsedPushV2 {
        require(envelopeJson.length <= MAX_ENVELOPE_CHARS) { "Sync envelope exceeds safety limit" }
        val obj = HouseholdSyncCodec.parseJsonObject(envelopeJson)
        val version = (obj["v"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("Missing envelope version")
        require(version == PROTOCOL_VERSION) { "Unsupported sync envelope version $version" }
        val nonceB64 = (obj["nonce"] as? String)?.trim().orEmpty()
        val nonce = decodeBase64(nonceB64)
        require(nonce.size == NONCE_BYTES) { "Invalid handshake nonce" }
        val rawX = decodeBase64(obj["x"] as? String ?: "")
        require(rawX.size in 1..MODULUS_BYTES) { "Invalid sender public element length" }
        val sender = HouseholdSyncCodec.sanitizeLabel(obj["sender"] as? String, maxLen = 64)
            .ifEmpty { "WeMi Device" }
        val iv = decodeBase64(obj["iv"] as? String ?: "")
        require(iv.size == IV_BYTES) { "Invalid IV length" }
        val ctRaw = (obj["ct"] as? String)?.trim().orEmpty()
        require(ctRaw.length <= ((HouseholdSyncCodec.MAX_JSON_BYTES + 16) * 4) / 3 + 16) {
            "Ciphertext exceeds safety limit"
        }
        val ciphertext = decodeBase64(ctRaw)
        require(ciphertext.size in 16..(HouseholdSyncCodec.MAX_JSON_BYTES + 16)) {
            "Invalid ciphertext length"
        }
        return ParsedPushV2(
            nonceB64 = nonceB64,
            nonceBytes = nonce,
            xRawBytes = rawX,
            senderLabel = sender,
            iv = iv,
            ciphertext = ciphertext
        )
    }

    data class ParsedPushV2(
        val nonceB64: String,
        val nonceBytes: ByteArray,
        val xRawBytes: ByteArray,
        val senderLabel: String,
        val iv: ByteArray,
        val ciphertext: ByteArray
    )

    /**
     * Completes the receiver side of SPAKE2 against a consumed [ServerChallenge] and decrypts the
     * AES-256-GCM payload. Throws [SecurityException] on invalid subgroup element, wrong PIN, or
     * tampered transcript/sender/ciphertext.
     */
    fun decryptPushV2(parsed: ParsedPushV2, challenge: ServerChallenge): Pair<String, String> {
        val xPublic = BigInteger(1, parsed.xRawBytes)
        if (!isValidSubgroupElement(xPublic)) {
            throw SecurityException("Invalid sender SPAKE2 subgroup element")
        }
        val xCanonical = toFixedUnsignedBytes(xPublic)
        val unblindedX = xPublic.multiply(challenge.mInvW).mod(P)
        val sharedK = unblindedX.modPow(challenge.yScalar, P)
        val kBytes = toFixedUnsignedBytes(sharedK)
        var keyBytes: ByteArray? = null
        var plainBytes: ByteArray? = null
        return try {
            keyBytes = deriveTranscriptAesKey(challenge.nonceBytes, xCanonical, challenge.yPublicBytes, kBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, parsed.iv))
            cipher.updateAAD(buildV2Aad(challenge.nonceBytes, xCanonical, challenge.yPublicBytes, parsed.senderLabel, parsed.iv))
            plainBytes = cipher.doFinal(parsed.ciphertext)
            require(plainBytes.size <= HouseholdSyncCodec.MAX_JSON_BYTES) {
                "Decrypted household payload exceeds safety limit"
            }
            parsed.senderLabel to String(plainBytes, Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw SecurityException("Invalid pairing code or corrupted transfer", e)
        } finally {
            Arrays.fill(kBytes, 0.toByte())
            keyBytes?.let { Arrays.fill(it, 0.toByte()) }
            plainBytes?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    internal fun isValidSubgroupElement(candidate: BigInteger): Boolean =
        candidate >= TWO && candidate <= P_MINUS_TWO && candidate.modPow(Q, P) == BigInteger.ONE

    private fun derivePasswordScalar(normalizedPin: String, salt: ByteArray): BigInteger {
        val pinChars = normalizedPin.toCharArray()
        val spec = PBEKeySpec(pinChars, salt, PBKDF2_ITERATIONS, 256)
        var derived: ByteArray? = null
        return try {
            derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            BigInteger(1, derived).mod(Q_MINUS_ONE).add(BigInteger.ONE)
        } finally {
            spec.clearPassword()
            Arrays.fill(pinChars, '\u0000')
            derived?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    private fun randomScalar(): BigInteger {
        val bytes = ByteArray(EPHEMERAL_EXPONENT_BITS / 8)
        try {
            while (true) {
                secureRandom.nextBytes(bytes)
                val v = BigInteger(1, bytes).mod(Q_MINUS_ONE).add(BigInteger.ONE)
                if (v >= TWO) return v
            }
        } finally {
            Arrays.fill(bytes, 0.toByte())
        }
    }

    private fun deriveSubgroupGenerator(domainLabel: String): BigInteger {
        val digest = MessageDigest.getInstance("SHA-256")
        val baos = ByteArrayOutputStream(MODULUS_BYTES)
        for (counter in 0 until 8) {
            digest.reset()
            digest.update(domainLabel.toByteArray(Charsets.US_ASCII))
            digest.update(counter.toByte())
            baos.write(digest.digest())
        }
        val base = BigInteger(1, baos.toByteArray()).mod(P_MINUS_THREE).add(TWO)
        // Squaring any base in [2, p-2] maps deterministically into prime-order subgroup G_q \ {1}
        val elem = base.modPow(TWO, P)
        check(isValidSubgroupElement(elem)) { "Derived generator outside prime-order subgroup" }
        return elem
    }

    private fun deriveTranscriptAesKey(
        nonce: ByteArray,
        xBytes: ByteArray,
        yBytes: ByteArray,
        kBytes: ByteArray
    ): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        updateLengthPrefixed(digest, KEY_DERIVATION_CONTEXT.toByteArray(Charsets.US_ASCII))
        updateLengthPrefixed(digest, nonce)
        updateLengthPrefixed(digest, xBytes)
        updateLengthPrefixed(digest, yBytes)
        updateLengthPrefixed(digest, kBytes)
        return digest.digest()
    }

    private fun buildV2Aad(
        nonce: ByteArray,
        xBytes: ByteArray,
        yBytes: ByteArray,
        senderLabel: String,
        iv: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream(640)
        writeLengthPrefixed(out, AAD_DOMAIN.toByteArray(Charsets.US_ASCII))
        writeLengthPrefixed(out, nonce)
        writeLengthPrefixed(out, xBytes)
        writeLengthPrefixed(out, yBytes)
        writeLengthPrefixed(out, senderLabel.toByteArray(Charsets.UTF_8))
        writeLengthPrefixed(out, iv)
        return out.toByteArray()
    }

    private fun updateLengthPrefixed(digest: MessageDigest, data: ByteArray) {
        val len = ByteBuffer.allocate(4).putInt(data.size).array()
        digest.update(len)
        digest.update(data)
    }

    private fun writeLengthPrefixed(out: ByteArrayOutputStream, data: ByteArray) {
        out.write(ByteBuffer.allocate(4).putInt(data.size).array())
        out.write(data)
    }

    internal fun toFixedUnsignedBytes(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val out = ByteArray(MODULUS_BYTES)
        val srcOffset = if (raw.size > 1 && raw[0] == 0.toByte()) 1 else 0
        val copyLen = raw.size - srcOffset
        require(copyLen in 1..MODULUS_BYTES) { "Group element out of 2048-bit range" }
        System.arraycopy(raw, srcOffset, out, MODULUS_BYTES - copyLen, copyLen)
        return out
    }

    private fun decodeBase64(encoded: String): ByteArray =
        try {
            Base64.Default.decode(encoded.trim())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Malformed Base64 in SPAKE2 message", e)
        }
}
