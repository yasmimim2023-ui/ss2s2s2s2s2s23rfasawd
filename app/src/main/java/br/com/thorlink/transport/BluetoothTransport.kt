package br.com.thorlink.transport

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.os.Build
import br.com.thorlink.domain.Peer
import br.com.thorlink.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.*
import java.security.KeyPair
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothTransport(private val context: Context, private val receiverRole: Boolean,
    private val onPeers: (List<Peer>, Boolean) -> Unit, private val onError: (String) -> Unit) {
    companion object {
        val CONTROL_UUID: UUID = UUID.fromString("257869d9-d78f-4b0b-bdaa-d09138a459a1")
        val BULK_UUID: UUID = UUID.fromString("257869d9-d78f-4b0b-bdaa-d09138a459a2")
    }
    val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val discovered = linkedMapOf<String, Peer>()
    private var registered = false
    private var server: BluetoothServerSocket? = null
    private var pending: BluetoothSocket? = null
    private var operation: Job? = null
    private val events = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND, BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val d = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (d != null) discovered[d.address] = Peer(d.address, d.name ?: "Android Bluetooth", d.bondState == BluetoothDevice.BOND_BONDED)
                    publish(adapter?.isDiscovering == true)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> publish(false)
                BluetoothAdapter.ACTION_STATE_CHANGED -> if (adapter?.isEnabled != true) { stop(); onError("Bluetooth foi desligado") }
            }
        }
    }
    private fun register() {
        if (registered) return
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        // Bluetooth broadcasts originate in the privileged Bluetooth process, not necessarily system UID.
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(events, f, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(events, f)
        registered = true
    }
    private fun ready(): BluetoothAdapter {
        val a = adapter ?: error("Este aparelho não possui Bluetooth Classic")
        check(a.isEnabled) { "Ative o Bluetooth para continuar" }; register(); return a
    }
    fun refresh() = runCatching {
        val a = ready(); a.bondedDevices.forEach { discovered[it.address] = Peer(it.address, it.name ?: "Android", true) }; publish(a.isDiscovering)
    }.onFailure { onError(it.message ?: "Permissão Bluetooth necessária") }
    private fun publish(scanning: Boolean) = onPeers(discovered.values.sortedWith(compareByDescending<Peer> { it.bonded }.thenBy { it.name }), scanning)
    fun discover() = runCatching {
        val a = ready(); discovered.clear(); refresh(); a.cancelDiscovery()
        check(a.startDiscovery()) { "Não foi possível buscar. Verifique permissões e, no Android 11 ou anterior, a localização do sistema." }
        publish(true)
    }.onFailure { onError(it.message ?: "Falha ao procurar") }
    fun pair(address: String) = runCatching {
        val a = ready(); a.cancelDiscovery(); check(a.getRemoteDevice(address).createBond()) { "Pareamento não iniciado; use as configurações Bluetooth" }
    }.onFailure { onError(it.message ?: "Falha no pareamento") }
    fun listen(onSocket: (BluetoothSocket) -> Unit) {
        stop()
        operation = scope.launch {
            runCatching {
                val a = ready(); a.cancelDiscovery()
                server = a.listenUsingRfcommWithServiceRecord("ThorLink", CONTROL_UUID)
                val s = server!!.accept(120_000)
                server?.close(); server = null
                if (s.remoteDevice.bondState != BluetoothDevice.BOND_BONDED) { s.close(); error("Pareie primeiro nas configurações do Android") }
                onSocket(s)
            }.onFailure { if (isActive) onError(it.message ?: "Tempo de espera encerrado") }
        }
    }
    fun connect(address: String, onSocket: (BluetoothSocket) -> Unit) {
        stop()
        operation = scope.launch {
            runCatching {
                val a = ready(); a.cancelDiscovery(); val d = a.getRemoteDevice(address)
                check(d.bondState == BluetoothDevice.BOND_BONDED) { "Pareamento Android obrigatório antes da conexão" }
                val s = d.createRfcommSocketToServiceRecord(CONTROL_UUID); pending = s
                val watchdog = scope.launch { delay(15_000); if (pending === s) s.close() }
                try { s.connect(); pending = null; onSocket(s) } finally { watchdog.cancel() }
            }.onFailure { if (isActive) onError("Conexão falhou: ${it.message}. Abra o receptor e toque em Receber.") }
        }
    }
    fun stop() {
        operation?.cancel(); operation = null
        runCatching { server?.close() }; server = null
        runCatching { pending?.close() }; pending = null
        runCatching { adapter?.cancelDiscovery() }
    }
    fun destroy() { stop(); if (registered) runCatching { context.unregisterReceiver(events) }; registered = false; scope.cancel() }
}

data class Handshake(val wire: SecureWire, val fingerprint: String, val code: String)

/** Mutual proof of long-term identity and ephemeral ECDH keys. New devices require comparing SAS on both displays. */
object Handshaker {
    private fun writeBlob(out: DataOutputStream, bytes: ByteArray) { require(bytes.size <= 2048); out.writeInt(bytes.size); out.write(bytes); out.flush() }
    private fun readBlob(input: DataInputStream): ByteArray { val n = input.readInt(); require(n in 1..2048); return ByteArray(n).also { input.readFully(it) } }
    fun establish(socket: BluetoothSocket, receiver: Boolean, identity: KeyPair): Handshake = establish(socket.inputStream,socket.outputStream,receiver,identity)
    fun establish(incoming: InputStream, outgoing: OutputStream, receiver: Boolean, identity: KeyPair): Handshake {
        val input = DataInputStream(incoming); val output = DataOutputStream(outgoing)
        val eph = Crypto.ec(); val nonce = Crypto.random(32)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.writeInt(3); it.writeBoolean(receiver); writeBlob(it, identity.public.encoded); writeBlob(it, eph.public.encoded); writeBlob(it, nonce) }
        // Commit before revealing public keys/nonces so a first-session MITM cannot adaptively grind the comparison code.
        val own = bytes.toByteArray(); writeBlob(output, Crypto.sha(own)); val commitment = readBlob(input)
        require(commitment.size == 32)
        writeBlob(output, own); val remote = readBlob(input)
        require(java.security.MessageDigest.isEqual(commitment,Crypto.sha(remote))) { "Compromisso de autenticação inválido" }
        val r = DataInputStream(ByteArrayInputStream(remote))
        require(r.readInt() == 3) { "Versões diferentes. Atualize os dois APKs para 1.1.0." }; require(r.readBoolean() != receiver) { "Instale Controle em um celular e Receptor no outro" }
        val remoteIdentity = Crypto.publicKey(readBlob(r)); val remoteEphemeral = Crypto.publicKey(readBlob(r))
        require(readBlob(r).size == 32 && r.available() == 0)
        val transcript = "ThorLink-v3".toByteArray() + (if (receiver) remote + own else own + remote)
        writeBlob(output, Crypto.sign(identity.private, transcript))
        require(Crypto.verify(remoteIdentity, transcript, readBlob(input))) { "Identidade inválida" }
        val secret = Crypto.agree(eph.private, remoteEphemeral); val salt = Crypto.sha(transcript)
        val c2r = Crypto.hkdf(secret, salt, "control-controller-to-receiver")
        val r2c = Crypto.hkdf(secret, salt, "control-receiver-to-controller")
        val code = Crypto.sas(Crypto.hkdf(secret,salt,"verification")+transcript); secret.fill(0)
        return Handshake(SecureWire(input, output, if (receiver) r2c else c2r, if (receiver) c2r else r2c, 16384),
            Crypto.hex(Crypto.sha(remoteIdentity.encoded)), code)
    }
    /** Bulk socket has fresh directional keys, bound to the consented control session's random secret. */
    fun bulk(socket: BluetoothSocket, receiver: Boolean, secret: ByteArray): SecureWire {
        val c2r = Crypto.hkdf(secret, Crypto.sha(secret), "bulk-controller-to-receiver")
        val r2c = Crypto.hkdf(secret, Crypto.sha(secret), "bulk-receiver-to-controller")
        return SecureWire(DataInputStream(socket.inputStream), DataOutputStream(socket.outputStream), if (receiver) r2c else c2r, if (receiver) c2r else r2c)
    }
}

/** Bounded reliable queue for protocol messages. Joystick states are sampled upstream, never accumulated per touch. */
class ControlChannel(private val socket: BluetoothSocket, private val wire: SecureWire,
    private val scope: CoroutineScope, onMessage: (ByteArray) -> Unit, private val onFailure: (Throwable) -> Unit) {
    private val outgoing = Channel<ByteArray>(64)
    private val writer = scope.launch(Dispatchers.IO) {
        try { for (bytes in outgoing) wire.send(bytes) } catch (e: Throwable) { if (isActive) onFailure(e) }
    }
    private val reader = scope.launch(Dispatchers.IO) {
        try { while (isActive) onMessage(wire.receive()) } catch (e: Throwable) { if (isActive) onFailure(e) }
    }
    fun send(bytes: ByteArray) { if (!outgoing.trySend(bytes).isSuccess) onFailure(IOException("Canal de controle congestionado")) }
    fun close() { outgoing.close(); writer.cancel(); reader.cancel(); runCatching { socket.close() } }
}
