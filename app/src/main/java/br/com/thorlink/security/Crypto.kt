package br.com.thorlink.security

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Small versioned protocol. Keys and nonces are independent in each direction. */
object Crypto {
    fun random(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }
    fun sha(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    fun ec(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    fun publicKey(bytes: ByteArray): PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    fun agree(privateKey: PrivateKey, publicKey: PublicKey): ByteArray = KeyAgreement.getInstance("ECDH").run {
        init(privateKey); doPhase(publicKey, true); generateSecret()
    }
    fun sign(key: PrivateKey, bytes: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run { initSign(key); update(bytes); sign() }
    fun verify(key: PublicKey, bytes: ByteArray, signature: ByteArray) = Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(bytes); verify(signature) }
    fun hkdf(secret: ByteArray, salt: ByteArray, label: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256")); val prk = mac.doFinal(secret)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        return mac.doFinal(label.toByteArray(Charsets.UTF_8) + byteArrayOf(1))
    }
    fun sas(transcript: ByteArray): String {
        val n = ByteBuffer.wrap(sha(transcript)).int.toLong() and 0xffffffffL
        return "%06d".format(n % 1_000_000)
    }
}

/** AES-256-GCM: sequence number authenticated as AAD; rejects replays and oversize allocations. */
class SecureWire(private val input: DataInputStream, private val output: DataOutputStream,
    sendKey: ByteArray, receiveKey: ByteArray, private val maxFrame: Int = 131072) {
    private val txKey = SecretKeySpec(sendKey, "AES")
    private val rxKey = SecretKeySpec(receiveKey, "AES")
    private var tx = 0L
    private var rx = 0L
    @Synchronized fun send(bytes: ByteArray) {
        require(bytes.size in 1..maxFrame)
        check(tx < Long.MAX_VALUE)
        val header = ByteBuffer.allocate(8).putLong(tx++).array()
        val nonce = byteArrayOf(0, 0, 0, 0) + header
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, txKey, GCMParameterSpec(128, nonce)); cipher.updateAAD(header)
        val encrypted = cipher.doFinal(bytes)
        val packet = ByteBuffer.allocate(4+header.size+encrypted.size).putInt(encrypted.size).put(header).put(encrypted).array()
        output.write(packet); output.flush()
    }
    fun receive(): ByteArray {
        val size = input.readInt(); require(size in 17..(maxFrame + 16)) { "Frame fora do limite" }
        val header = ByteArray(8).also { input.readFully(it) }
        require(ByteBuffer.wrap(header).long == rx) { "Sequência inválida" }
        val bytes = ByteArray(size).also { input.readFully(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, rxKey, GCMParameterSpec(128, byteArrayOf(0,0,0,0) + header))
        cipher.updateAAD(header)
        val plain = cipher.doFinal(bytes); rx++; return plain
    }
}
