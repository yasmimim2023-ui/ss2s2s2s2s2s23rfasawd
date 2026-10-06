package br.com.thorlink.security

import org.junit.Assert.*
import org.junit.Test
import java.io.*

class CryptoTest {
    @Test fun ephemeralAgreementAndMutualSignatures() {
        val a=Crypto.ec();val b=Crypto.ec();val id=Crypto.ec()
        assertArrayEquals(Crypto.agree(a.private,b.public),Crypto.agree(b.private,a.public))
        val transcript=a.public.encoded+b.public.encoded
        val signature=Crypto.sign(id.private,transcript)
        assertTrue(Crypto.verify(id.public,transcript,signature))
        assertFalse(Crypto.verify(id.public,transcript+byteArrayOf(0),signature))
    }
    @Test fun hkdfIsDeterministicAndDirectionKeysDiffer() {
        val secret=Crypto.random(32);val salt=Crypto.random(32)
        assertArrayEquals(Crypto.hkdf(secret,salt,"a"),Crypto.hkdf(secret,salt,"a"))
        assertFalse(Crypto.hkdf(secret,salt,"a").contentEquals(Crypto.hkdf(secret,salt,"b")))
        assertTrue(Crypto.sas(secret).matches(Regex("\\d{6}")))
    }
    private fun packet(key:ByteArray):ByteArray {
        val out=ByteArrayOutputStream()
        SecureWire(DataInputStream(ByteArrayInputStream(byteArrayOf())),DataOutputStream(out),key,key).send("authorized".toByteArray())
        return out.toByteArray()
    }
    private fun reader(bytes:ByteArray,key:ByteArray)=SecureWire(DataInputStream(ByteArrayInputStream(bytes)),DataOutputStream(ByteArrayOutputStream()),key,key)
    @Test fun encryptedRoundTripAndTamperingRejected() {
        val key=Crypto.random(32);val p=packet(key)
        assertEquals("authorized",reader(p,key).receive().toString(Charsets.UTF_8))
        p[p.lastIndex]=(p.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java){reader(p,key).receive()}
    }
    @Test fun replayRejected() {
        val key=Crypto.random(32);val p=packet(key);val wire=reader(p+p,key)
        wire.receive();assertThrows(IllegalArgumentException::class.java){wire.receive()}
    }
    @Test fun oversizedFramesRejectedBeforeAllocation() {
        val bytes=ByteArrayOutputStream().apply{DataOutputStream(this).writeInt(Int.MAX_VALUE)}.toByteArray()
        assertThrows(IllegalArgumentException::class.java){reader(bytes,Crypto.random(32)).receive()}
    }
    @Test fun wrongDirectionKeyRejected() {
        val key=Crypto.random(32)
        assertThrows(Exception::class.java){reader(packet(key),Crypto.random(32)).receive()}
    }
}
