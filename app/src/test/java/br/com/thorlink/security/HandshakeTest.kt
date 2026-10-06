package br.com.thorlink.security

import br.com.thorlink.transport.Handshaker
import br.com.thorlink.transport.Handshake
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.concurrent.*

/** Full real handshake and encrypted duplex data over local streams; Bluetooth hardware is not emulated. */
class HandshakeTest {
    @Test fun bothRolesAuthenticateAndExchangeEncryptedMessages() {
        val cIdentity=Crypto.ec();val rIdentity=Crypto.ec()
        val cIn=PipedInputStream(65536);val rOut=PipedOutputStream(cIn)
        val rIn=PipedInputStream(65536);val cOut=PipedOutputStream(rIn)
        val executor=Executors.newFixedThreadPool(2)
        try {
            val c=executor.submit<Handshake>{Handshaker.establish(cIn,cOut,false,cIdentity)}
            val r=executor.submit<Handshake>{Handshaker.establish(rIn,rOut,true,rIdentity)}
            val controller=c.get(5,TimeUnit.SECONDS);val receiver=r.get(5,TimeUnit.SECONDS)
            assertEquals(controller.code,receiver.code)
            assertEquals(Crypto.hex(Crypto.sha(rIdentity.public.encoded)),controller.fingerprint)
            assertEquals(Crypto.hex(Crypto.sha(cIdentity.public.encoded)),receiver.fingerprint)
            controller.wire.send("joystick".toByteArray());assertEquals("joystick",receiver.wire.receive().toString(Charsets.UTF_8))
            receiver.wire.send("consent".toByteArray());assertEquals("consent",controller.wire.receive().toString(Charsets.UTF_8))
        } finally {executor.shutdownNow();cIn.close();rIn.close();cOut.close();rOut.close()}
    }
    @Test fun twoReceiversCannotFormASession() {
        val aIn=PipedInputStream(65536);val bOut=PipedOutputStream(aIn)
        val bIn=PipedInputStream(65536);val aOut=PipedOutputStream(bIn)
        val executor=Executors.newFixedThreadPool(2)
        try {
            val a=executor.submit<Handshake>{Handshaker.establish(aIn,aOut,true,Crypto.ec())}
            val b=executor.submit<Handshake>{Handshaker.establish(bIn,bOut,true,Crypto.ec())}
            assertThrows(ExecutionException::class.java){a.get(5,TimeUnit.SECONDS)}
            assertThrows(ExecutionException::class.java){b.get(5,TimeUnit.SECONDS)}
        } finally{executor.shutdownNow();aIn.close();bIn.close();aOut.close();bOut.close()}
    }
    @Test fun changedRevealDoesNotMatchCommitment() {
        val incoming=ByteArrayOutputStream()
        DataOutputStream(incoming).use { out ->
            val commitment=Crypto.sha("original".toByteArray());out.writeInt(commitment.size);out.write(commitment)
            val forged="changed".toByteArray();out.writeInt(forged.size);out.write(forged)
        }
        assertThrows(IllegalArgumentException::class.java){Handshaker.establish(ByteArrayInputStream(incoming.toByteArray()),ByteArrayOutputStream(),false,Crypto.ec())}
    }
    @Test fun oldAppVersionRequiresUpdatingBothApks() {
        val oldHello=ByteArrayOutputStream().apply { DataOutputStream(this).writeInt(1) }.toByteArray()
        val incoming=ByteArrayOutputStream()
        DataOutputStream(incoming).use { out ->
            val commitment=Crypto.sha(oldHello);out.writeInt(commitment.size);out.write(commitment)
            out.writeInt(oldHello.size);out.write(oldHello)
        }
        val failure=assertThrows(IllegalArgumentException::class.java) {
            Handshaker.establish(ByteArrayInputStream(incoming.toByteArray()),ByteArrayOutputStream(),false,Crypto.ec())
        }
        assertTrue(failure.message!!.contains("Atualize os dois APKs"))
    }
}
