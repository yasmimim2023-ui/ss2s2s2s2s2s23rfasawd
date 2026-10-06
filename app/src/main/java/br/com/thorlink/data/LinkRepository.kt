package br.com.thorlink.data

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import br.com.thorlink.domain.*
import br.com.thorlink.security.*
import br.com.thorlink.services.*
import br.com.thorlink.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("MissingPermission")
class LinkRepository(private val context: Context, val receiver: Boolean) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = LocalStore(context)
    private val _state = MutableStateFlow(UiState(authorized = store.authorized(), history = store.history(),
        targetPackage = store.target(), targetLabel = store.targetLabel(), mapping = store.mapping(),controlMode=store.controlMode()))
    val state: StateFlow<UiState> = _state.asStateFlow()
    val bluetooth = BluetoothTransport(context, receiver, { peers, scanning ->
        _state.update { it.copy(peers = peers, status = if (it.status in listOf(LinkStatus.IDLE, LinkStatus.DISCOVERING))
            (if (scanning) LinkStatus.DISCOVERING else LinkStatus.IDLE) else it.status) }
    }, ::error)
    @Volatile private var epoch = 0L
    private var socket: BluetoothSocket? = null
    @Volatile private var channel: ControlChannel? = null
    private var bulkSocket: BluetoothSocket? = null
    private var bulkServer: BluetoothServerSocket? = null
    @Volatile private var bulk: SecureWire? = null
    private var bulkRequested = false
    private val bulkLock = Mutex()
    private var sessionJobs: Job? = null
    @Volatile private var localConfirmed = false
    @Volatile private var remoteConfirmed = false
    private var remoteGrants = Grants()
    private var address = ""
    private var fingerprint = ""
    @Volatile private var lastPacket = 0L
    @Volatile private var lastControl = 0L
    private val outgoingControls = ControlInputBuffer()
    private val gestureControls = ControlInputBuffer()
    @Volatile private var inputAck = true
    private var inputSentAt = 0L
    @Volatile private var inputSequence = 0L
    private var inputSentButtons = 0
    private var nativePad:HidGamepad?=null
    @Volatile private var nativeLeaseAt=0L
    @Volatile private var nativeLeaseAllowed=false
    private val latestFrame = AtomicReference<ByteArray?>(null)
    private var frameSentAt = 0L
    @Volatile private var frameWaitingAck = false
    @Volatile private var frameSequence = 0L
    private var incomingStream: OutputStream? = null
    private var incomingUri: Uri? = null
    private var incomingDigest: MessageDigest? = null
    @Volatile private var incomingOffer: FileOffer? = null
    @Volatile private var incomingCount = 0L
    private var outgoingUri: Uri? = null
    @Volatile private var outgoingOffer: FileOffer? = null
    private var outgoingJob: Job? = null
    private var chunkAck = CompletableDeferred<Long>()
    private var watchdog: Job? = null
    @Volatile private var acceptingFiles = false
    @Volatile var externalFlow = false
    @Volatile private var uiVisible = true

    fun error(message: String) { disconnect(message, true) }
    fun refresh() { bluetooth.refresh() }
    fun scan() { if (_state.value.status !in listOf(LinkStatus.CONNECTED, LinkStatus.VERIFYING, LinkStatus.CONNECTING)) bluetooth.discover() }
    fun pair(peer: Peer) { bluetooth.pair(peer.address); _state.update { it.copy(message = "Confirme o pareamento no Android dos dois celulares. Depois toque em Conectar.") } }
    fun listen() {
        if (!receiver) return
        disconnect()
        _state.update { it.copy(status = LinkStatus.LISTENING, message = "Receptor aberto por 2 minutos. Nenhum dado será compartilhado antes da aprovação.") }
        val current = epoch
        bluetooth.listen { s -> if (epoch == current) establish(s) else s.close() }
    }
    fun connect(peer: Peer) {
        if (receiver) return
        disconnect()
        _state.update { it.copy(status = LinkStatus.CONNECTING, remoteName = peer.name, message = "Conectando ao receptor pareado…") }
        val current = epoch
        bluetooth.connect(peer.address) { s -> if (epoch == current) establish(s) else s.close() }
    }
    private fun establish(s: BluetoothSocket) {
        val current = epoch
        socket = s; address = s.remoteDevice.address
        val name = s.remoteDevice.name ?: "Android pareado"
        val timeout = scope.launch { delay(20_000); if (epoch == current && _state.value.status != LinkStatus.VERIFYING) error("Autenticação excedeu o tempo limite") }
        scope.launch {
            try {
                check(s.remoteDevice.bondState == BluetoothDevice.BOND_BONDED)
                val h = Handshaker.establish(s, receiver, Identity.get()); timeout.cancel()
                if (epoch != current) { s.close(); return@launch }
                val remembered = store.authorized().find { it.address == address }
                check(remembered == null || remembered.fingerprint == h.fingerprint) {
                    "A identidade deste aparelho mudou. Remova a autorização antiga e confira o código novamente."
                }
                fingerprint = h.fingerprint; localConfirmed = false; remoteConfirmed = false
                _state.update { it.copy(status = LinkStatus.VERIFYING, remoteName = name, fingerprint = fingerprint,
                    code = h.code, localConfirmed = false, grants = Grants(), message = "Compare os 6 dígitos nos dois aparelhos antes de aprovar.") }
                lastPacket = SystemClock.elapsedRealtime()
                channel = ControlChannel(s, h.wire, scope, { bytes ->
                    if (epoch == current) {
                        try { lastPacket = SystemClock.elapsedRealtime(); handle(JSONObject(bytes.toString(Charsets.UTF_8))) }
                        catch (e: Exception) { error("Protocolo inválido: ${e.message}") }
                    }
                }, { if (epoch == current) error("Conexão interrompida: ${it.message ?: "Bluetooth indisponível"}") })
                scope.launch { delay(90_000); if (epoch == current && _state.value.status == LinkStatus.VERIFYING) error("A aprovação expirou. Conecte novamente.") }
            } catch (e: Exception) { timeout.cancel(); if (epoch == current) error(e.message ?: "Falha ao autenticar") }
        }
    }
    @Synchronized fun confirm(grants: Grants) {
        if (_state.value.status != LinkStatus.VERIFYING || localConfirmed) return
        val approved=grants.copy(nativeGamepad=receiver && grants.controls && _state.value.controlMode==ControlMode.HID)
        if (receiver && approved.controls && !approved.nativeGamepad && _state.value.targetPackage.isNotEmpty() && !_state.value.touchAvailable) {
            _state.update { it.copy(message = "Ative manualmente a Acessibilidade antes de autorizar controles externos.") }; return
        }
        localConfirmed = true
        _state.update { it.copy(localConfirmed = true, grants = if (receiver) approved else it.grants, message = "Sua confirmação foi enviada. Aguardando o outro aparelho.") }
        send(JSONObject().put("t", "CONFIRM").put("g", grantsJson(if (receiver) approved else Grants())))
        activateIfApproved()
    }
    @Synchronized private fun activateIfApproved() {
        if (!localConfirmed || !remoteConfirmed || _state.value.status != LinkStatus.VERIFYING) return
        val grants = if (receiver) _state.value.grants else remoteGrants
        store.remember(AuthorizedPeer(address, _state.value.remoteName, fingerprint))
        store.log(_state.value.remoteName, "Sessão aprovada pelos dois usuários")
        _state.update { it.copy(status = LinkStatus.CONNECTED, grants = grants, authorized = store.authorized(), history = store.history(),
            controlHealth = if(grants.nativeGamepad)ControlHealth.HID_WAITING else ControlHealth.WAITING, inputCount = 0, lastButtons = 0,
            message = "Sessão autenticada • compartilhamento autorizado") }
        TouchService.instance?.configureSession(grants.controls && !grants.nativeGamepad)
        context.startForegroundService(Intent(context, SessionService::class.java))
        if(!receiver && grants.nativeGamepad)startNativeGamepad()
        if (receiver) {
            refreshControlHealth()
            if (grants.name || grants.model || grants.battery) shareInfo()
            startBulkServer()
        }
        sessionJobs = scope.launch {
            launch {
                while (isActive) {
                    delay(2_000)
                    if (SystemClock.elapsedRealtime() - lastPacket > 8_000) { error("O outro aparelho deixou de responder"); break }
                    send(JSONObject().put("t", "PING").put("at", SystemClock.elapsedRealtime()))
                }
            }
            if (!receiver) launch {
                // Axes are conflated; button edges survive ACK backpressure, so quick taps are not lost.
                while (isActive) {
                    delay(20)
                    if (grants.controls && inputAck) {
                        inputAck = false; inputSentAt = SystemClock.elapsedRealtime()
                        val c = outgoingControls.next(inputSentAt); inputSentButtons = c.buttons
                        send(JSONObject().put("t", "INPUT").put("seq", ++inputSequence)
                            .put("lx", c.lx).put("ly", c.ly).put("rx", c.rx).put("ry", c.ry).put("b", c.buttons))
                    }
                }
            }
            if (receiver) launch {
                while (isActive) {
                    delay(50)
                    if (SystemClock.elapsedRealtime() - lastControl > 250 && _state.value.control != ControlState()) neutralize()
                    refreshControlHealth()
                    // HID reports stop within 500 ms if the authenticated receiver stops renewing this lease.
                    if(grants.nativeGamepad)send(JSONObject().put("t","HID_LEASE")
                        .put("allowed",!context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked))
                }
            }
        }
    }
    private fun send(o: JSONObject) { channel?.send(o.toString().toByteArray(Charsets.UTF_8)) }
    private fun grantsJson(g: Grants) = JSONObject().put("name",g.name).put("model",g.model).put("battery",g.battery).put("files",g.files).put("screen",g.screen).put("controls",g.controls).put("native",g.nativeGamepad)
    private fun parseGrants(o: JSONObject) = Grants(o.getBoolean("name"),o.getBoolean("model"),o.getBoolean("battery"),o.getBoolean("files"),o.getBoolean("screen"),o.getBoolean("controls"),o.getBoolean("native"))
        .also { require(!it.nativeGamepad || it.controls) }
    private fun handle(o: JSONObject) {
        val t = o.getString("t")
        if (t == "CONFIRM") {
            require(_state.value.status == LinkStatus.VERIFYING && !remoteConfirmed)
            val g = parseGrants(o.getJSONObject("g")); if (receiver) require(g == Grants())
            remoteGrants = g; remoteConfirmed = true; activateIfApproved(); return
        }
        // No information, file offer, screen frame or command is accepted before both confirmations.
        require(_state.value.status == LinkStatus.CONNECTED) { "Dados antes do consentimento" }
        val grants = _state.value.grants
        when(t) {
            "PING" -> send(JSONObject().put("t", "PONG").put("at", o.getLong("at")))
            "PONG" -> _state.update { it.copy(rttMs = (SystemClock.elapsedRealtime() - o.getLong("at")).coerceIn(0, 60000).toInt()) }
            "INFO" -> {
                require(!receiver)
                val name = if (grants.name) o.optString("name").take(100) else null
                val model = if (grants.model) o.optString("model").take(100) else null
                val battery = if (grants.battery) o.optInt("battery", -1).takeIf { it in 0..100 } else null
                _state.update { it.copy(deviceInfo = DeviceInfo(name, model, battery)) }
            }
            "INPUT" -> {
                require(receiver && grants.controls)
                fun axis(key: String) = o.getDouble(key).toFloat().also { require(it.isFinite() && it in -1f..1f) }
                val c = ControlState(axis("lx"),axis("ly"),axis("rx"),axis("ry"),o.getInt("b").also { require(it in 0..65535) })
                lastControl = SystemClock.elapsedRealtime()
                _state.update { it.copy(control = c, inputCount = it.inputCount + 1,
                    lastButtons = if(c.buttons != 0) c.buttons else it.lastButtons) }
                // The gesture pump consumes button edges independently of network ACK timing.
                if (touchAllowed()) {
                    if (!gestureControls.offer(c, lastControl)) reportControlHealth(ControlHealth.INPUT_OVERFLOW)
                } else gestureControls.reset()
                refreshControlHealth()
                send(JSONObject().put("t", "INPUT_ACK").put("seq", o.getLong("seq")))
            }
            "INPUT_ACK" -> { require(!receiver); if (o.getLong("seq") == inputSequence) {
                _state.update { it.copy(rttMs=(SystemClock.elapsedRealtime()-inputSentAt).coerceIn(0,60000).toInt(),
                    inputCount=it.inputCount+1, lastButtons=if(inputSentButtons != 0) inputSentButtons else it.lastButtons) }; inputAck = true
            } }
            "CONTROL_STATUS" -> {
                require(!receiver && grants.controls && !grants.nativeGamepad)
                val health = ControlHealth.valueOf(o.getString("status"))
                _state.update { it.copy(controlHealth = health) }
            }
            "HID_LEASE" -> {
                require(!receiver && grants.controls && grants.nativeGamepad)
                nativeLeaseAllowed=o.getBoolean("allowed");nativeLeaseAt=SystemClock.elapsedRealtime()
            }
            "HID_STATUS" -> {
                require(receiver && grants.controls && grants.nativeGamepad)
                val health=ControlHealth.valueOf(o.getString("status"))
                require(health.name.startsWith("HID_") || health==ControlHealth.INPUT_OVERFLOW)
                _state.update { it.copy(controlHealth=health,hidConnected=o.getBoolean("connected")) }
            }
            "BULK" -> { require(!receiver && !bulkRequested); bulkRequested=true; val secret = Base64.getDecoder().decode(o.getString("key")); require(secret.size == 32); connectBulk(secret) }
            "FRAME_ACK" -> { require(receiver); if (o.getLong("seq") == frameSequence) {
                frameWaitingAck = false
                _state.update { it.copy(frameAgeMs = (SystemClock.elapsedRealtime() - frameSentAt).coerceAtLeast(0)) }
            } }
            "SCREEN_OFF" -> { require(!receiver); _state.update { it.copy(frame = null, screenActive = false) } }
            "SCREEN_PAUSE" -> { require(!receiver && grants.screen); _state.update { it.copy(frame = null,screenActive=false,message="Captura pausada pelo Android: o aplicativo compartilhado não está visível") } }
            "FILE_OFFER" -> {
                require(grants.files && incomingOffer == null && outgoingOffer == null)
                val offer = FileOffer(o.getString("id"), o.getString("name").replace(Regex("[\\\\/\\p{Cntrl}]"), "_").take(120),o.getString("mime").take(100),o.getLong("size"))
                UUID.fromString(offer.id); require(offer.size in 0..1_073_741_824L)
                incomingOffer = offer; _state.update { it.copy(offer = offer, message = "Escolha onde salvar para aceitar ${offer.name}") }
            }
            "FILE_ACCEPT" -> { require(grants.files && outgoingJob==null && outgoingOffer?.id == o.getString("id")); sendFile() }
            "FILE_REJECT" -> { require(outgoingOffer?.id == o.getString("id")); clearTransfer("Transferência recusada") }
            "FILE_ACK" -> { require(outgoingOffer?.id == o.getString("id")); chunkAck.complete(o.getLong("offset")) }
            "FILE_END" -> finishIncoming(o)
            "FILE_DONE" -> { require(outgoingOffer?.id == o.getString("id")); clearTransfer("Arquivo enviado e verificado") }
            "FILE_CANCEL" -> clearTransfer("Transferência cancelada. O arquivo parcial não foi validado.")
            else -> error("Tipo de mensagem desconhecido")
        }
    }
    private fun shareInfo() {
        val g = _state.value.grants; val o = JSONObject().put("t","INFO")
        if (g.name) o.put("name", bluetooth.adapter?.name ?: "Android")
        if (g.model) o.put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
        if (g.battery) o.put("battery", context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        send(o)
    }
    private fun startBulkServer() {
        val current = epoch; val secret = Crypto.random(32)
        scope.launch {
            try {
                val a = bluetooth.adapter ?: kotlin.error("Bluetooth indisponível")
                bulkServer = a.listenUsingRfcommWithServiceRecord("ThorLink Data", BluetoothTransport.BULK_UUID)
                send(JSONObject().put("t","BULK").put("key",Base64.getEncoder().encodeToString(secret)))
                val s = bulkServer!!.accept(15_000); bulkServer?.close(); bulkServer = null
                if (epoch != current) { s.close(); return@launch }
                bulkSocket = s
                require(s.remoteDevice.address == address && s.remoteDevice.bondState == BluetoothDevice.BOND_BONDED)
                val w = Handshaker.bulk(s, true, secret)
                val timeout = scope.launch { delay(5000); if (bulk == null && epoch == current) s.close() }
                require(w.receive().contentEquals("BULK_READY".toByteArray())); w.send("BULK_OK".toByteArray()); timeout.cancel()
                bulk = w; startBulkReader(w, current); startFrameSender(w, current)
                _state.update { it.copy(bulkReady = true) }
            } catch (e: Exception) { if (epoch == current) { runCatching { bulkSocket?.close() };runCatching { bulkServer?.close() }; _state.update { it.copy(message = "Canal de dados indisponível: ${e.message}. Reconecte para tela/arquivos.", bulkReady = false) } } }
            finally { secret.fill(0) }
        }
    }
    private fun connectBulk(secret: ByteArray) {
        val current = epoch
        scope.launch {
            try {
                val s = bluetooth.adapter!!.getRemoteDevice(address).createRfcommSocketToServiceRecord(BluetoothTransport.BULK_UUID)
                bulkSocket = s
                val timeout = scope.launch { delay(15_000); if (bulk == null && epoch == current) s.close() }
                s.connect(); val w = Handshaker.bulk(s, false, secret)
                w.send("BULK_READY".toByteArray()); require(w.receive().contentEquals("BULK_OK".toByteArray())); timeout.cancel()
                if (epoch != current) { s.close(); return@launch }
                bulk = w; startBulkReader(w, current); _state.update { it.copy(bulkReady = true) }
            } catch (e: Exception) { if (epoch == current) { runCatching { bulkSocket?.close() };_state.update { it.copy(message = "Canal de dados indisponível: ${e.message}. Reconecte.", bulkReady = false) } } }
            finally { secret.fill(0) }
        }
    }
    private fun startBulkReader(w: SecureWire, current: Long) = scope.launch {
        try {
            while (isActive && epoch == current) {
                val bytes = w.receive(); val input = DataInputStream(ByteArrayInputStream(bytes))
                when (input.readUnsignedByte()) {
                    1 -> {
                        require(!receiver && _state.value.grants.screen)
                        val seq = input.readLong(); val jpeg = ByteArray(input.available()).also { input.readFully(it) }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(jpeg,0,jpeg.size,bounds)
                        require(bounds.outWidth in 1..800 && bounds.outHeight in 1..800)
                        val bmp = BitmapFactory.decodeByteArray(jpeg,0,jpeg.size) ?: kotlin.error("Imagem inválida")
                        _state.update { it.copy(frame = bmp, screenActive = true) }
                        send(JSONObject().put("t","FRAME_ACK").put("seq",seq))
                    }
                    2 -> receiveChunk(input)
                    else -> error("Dados desconhecidos")
                }
            }
        } catch (e: Exception) { if (epoch == current) error("Canal de dados interrompido: ${e.message}") }
    }
    private fun startFrameSender(w: SecureWire, current: Long) = scope.launch {
        while (isActive && epoch == current) {
            delay(50)
            if (!_state.value.screenActive || _state.value.transfer.active || frameWaitingAck) continue
            val jpeg = latestFrame.getAndSet(null) ?: continue
            frameWaitingAck = true; frameSentAt = SystemClock.elapsedRealtime(); frameSequence++
            val bytes = ByteArrayOutputStream().apply { DataOutputStream(this).apply { writeByte(1); writeLong(frameSequence); write(jpeg) } }.toByteArray()
            try { bulkLock.withLock { w.send(bytes) } }
            catch (e: Exception) { if (epoch == current) error("Imagem interrompida: ${e.message}"); break }
            // Screen stream drops old frames; control uses another RFCOMM socket.
        }
    }
    fun canCapture() = receiver && _state.value.status == LinkStatus.CONNECTED && _state.value.grants.screen && _state.value.bulkReady && !_state.value.transfer.active
    fun submitFrame(jpeg: ByteArray) { if (canCapture() && jpeg.size <= 100_000) latestFrame.set(jpeg) }
    fun screenStarted() { _state.update { it.copy(screenActive = true) } }
    fun screenPaused() { latestFrame.set(null); if (receiver && _state.value.status==LinkStatus.CONNECTED) send(JSONObject().put("t","SCREEN_PAUSE")) }
    fun screenStopped() { latestFrame.set(null); frameWaitingAck = false; _state.update { it.copy(screenActive = false) }; if (_state.value.status == LinkStatus.CONNECTED && receiver) send(JSONObject().put("t","SCREEN_OFF")) }
    fun controls(c: ControlState) {
        if (!receiver && _state.value.status == LinkStatus.CONNECTED && _state.value.grants.controls && uiVisible) {
            if (!outgoingControls.offer(c, SystemClock.elapsedRealtime()))
                _state.update { it.copy(message="Comandos congestionados. Solte os controles e tente novamente.") }
            if(Build.VERSION.SDK_INT>=28 && _state.value.grants.nativeGamepad)nativePad?.offer(c)
        }
    }
    fun neutralize() {
        outgoingControls.reset(); gestureControls.reset()
        if(Build.VERSION.SDK_INT>=28)nativePad?.neutralize()
        _state.update { it.copy(control = ControlState()) }; TouchService.instance?.release()
    }
    fun touchAvailable(available: Boolean) {
        _state.update { it.copy(touchAvailable = available) }
        if (!available) gestureControls.reset()
        refreshControlHealth()
    }
    fun foregroundPackage(pkg: String) {
        _state.update { it.copy(foregroundTarget = pkg == it.targetPackage && it.targetPackage.isNotEmpty()) }
        if (!_state.value.foregroundTarget) { gestureControls.reset(); TouchService.instance?.release() }
        refreshControlHealth()
    }
    /** Fail closed on focus, consent, accessibility, lock screen or stale input; never read UI content. */
    fun controlGate(): ControlHealth? {
        val s = _state.value
        return when {
            !receiver || s.status != LinkStatus.CONNECTED || !s.grants.controls || s.grants.nativeGamepad -> ControlHealth.WAITING
            s.targetPackage.isEmpty() -> ControlHealth.TEST_MODE
            !s.touchAvailable -> ControlHealth.ACCESSIBILITY_OFF
            context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked -> ControlHealth.LOCKED
            !s.foregroundTarget -> ControlHealth.OPEN_TARGET
            SystemClock.elapsedRealtime() - lastControl > 250 -> ControlHealth.INPUT_STALE
            else -> null
        }
    }
    fun touchAllowed() = controlGate() == null
    fun nextGestureControl(): ControlState = if(touchAllowed()) gestureControls.next(SystemClock.elapsedRealtime())
        else { gestureControls.reset(); ControlState() }
    fun refreshControlHealth() {
        if (!receiver || _state.value.status != LinkStatus.CONNECTED || !_state.value.grants.controls || _state.value.grants.nativeGamepad) return
        val gate = controlGate()
        if (gate != null) { gestureControls.reset(); reportControlHealth(gate) }
        else if (_state.value.controlHealth in setOf(ControlHealth.WAITING,ControlHealth.TEST_MODE,
            ControlHealth.ACCESSIBILITY_OFF,ControlHealth.LOCKED,ControlHealth.OPEN_TARGET,ControlHealth.INPUT_STALE))
            reportControlHealth(ControlHealth.READY)
    }
    @Synchronized fun reportControlHealth(health: ControlHealth) {
        if (!receiver || _state.value.status != LinkStatus.CONNECTED || !_state.value.grants.controls || _state.value.grants.nativeGamepad || _state.value.controlHealth == health) return
        _state.update { it.copy(controlHealth = health) }
        // Fixed status codes only: no foreground package name, window text, screenshot or private data.
        send(JSONObject().put("t","CONTROL_STATUS").put("status",health.name))
    }
    fun setTarget(pkg: String, label: String) {
        neutralize()
        if (_state.value.screenActive) context.stopService(Intent(context,ScreenService::class.java))
        store.target(pkg,label); _state.update { it.copy(targetPackage = pkg, targetLabel = label, foregroundTarget = false) }
        refreshControlHealth()
    }
    fun setMapping(m: Mapping) { neutralize(); store.mapping(m); _state.update { it.copy(mapping = m) } }
    fun setControlMode(mode:ControlMode) {
        if(!receiver || _state.value.controlMode==mode)return
        if(_state.value.status !in listOf(LinkStatus.IDLE,LinkStatus.ERROR))disconnect("Modo alterado. Conecte e autorize novamente.")
        store.controlMode(mode);_state.update{it.copy(controlMode=mode)}
    }
    private fun nativeAllowed():Boolean {
        val s=_state.value
        return !receiver && HidSendGate.allowed(s.status==LinkStatus.CONNECTED,s.grants.controls && s.grants.nativeGamepad,
            uiVisible,nativeLeaseAllowed,nativeLeaseAt,SystemClock.elapsedRealtime())
    }
    fun startNativeGamepad() {
        val s=_state.value
        if(receiver || s.status!=LinkStatus.CONNECTED || !s.grants.controls || !s.grants.nativeGamepad || !uiVisible)return
        if(Build.VERSION.SDK_INT<28) { nativeStatus(ControlHealth.HID_UNSUPPORTED,false);return }
        if(nativePad==null)nativePad=HidGamepad(context,::nativeAllowed,::nativeStatus) {
            if(_state.value.status==LinkStatus.CONNECTED && _state.value.grants.nativeGamepad)_state.update{it.copy(hidReports=it.hidReports+1)}
        }
        nativePad?.start(address)
    }
    private fun nativeStatus(health:ControlHealth,connected:Boolean) {
        val s=_state.value
        if(receiver || s.status!=LinkStatus.CONNECTED || !s.grants.nativeGamepad || (s.controlHealth==health && s.hidConnected==connected))return
        _state.update{it.copy(controlHealth=health,hidConnected=connected)}
        send(JSONObject().put("t","HID_STATUS").put("status",health.name).put("connected",connected))
    }
    // These are input events delivered normally to OUR foreground Activity, never events read from a game.
    fun nativeInput(c:ControlState) {
        if(receiver && _state.value.status==LinkStatus.CONNECTED && _state.value.grants.nativeGamepad)
            _state.update { it.copy(nativeControl=c,nativeInputCount=it.nativeInputCount+1) }
    }
    fun forget(peer: AuthorizedPeer) { if (peer.address == address && socket != null) disconnect("Autorização removida"); store.forget(peer.address); _state.update { it.copy(authorized = store.authorized()) } }
    fun clearHistory() { store.clearHistory(); _state.update { it.copy(history = emptyList()) } }
    fun visible(value: Boolean) {
        uiVisible = value
        if(receiver && _state.value.grants.nativeGamepad)_state.update{it.copy(nativeControl=ControlState())}
        if (value && _state.value.status==LinkStatus.IDLE && _state.value.message=="Aplicativo fechado") _state.update { it.copy(message="Somente com autorização dos dois aparelhos") }
        if (!value && !externalFlow && _state.value.status in listOf(LinkStatus.LISTENING,LinkStatus.CONNECTING,LinkStatus.VERIFYING,LinkStatus.DISCOVERING)) disconnect("Abra o aplicativo para iniciar e aprovar a conexão")
        if (!value && !receiver) outgoingControls.reset()
        if (!value && !receiver && _state.value.grants.nativeGamepad && Build.VERSION.SDK_INT>=28)nativePad?.stop(paused=true)
    }
    fun offerFile(uri: Uri) {
        if (_state.value.status != LinkStatus.CONNECTED || !_state.value.grants.files || !_state.value.bulkReady) { _state.update { it.copy(message = "Arquivos precisam estar autorizados e o canal de dados conectado") }; return }
        if (incomingOffer != null || outgoingOffer != null) return
        scope.launch {
            runCatching {
                var name = "arquivo"; var size = -1L
                context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use { c ->
                    if (c.moveToFirst()) { name = c.getString(0) ?: "arquivo"; if (!c.isNull(1)) size = c.getLong(1) }
                }
                require(size in 0..1_073_741_824L) { "Selecione um arquivo de tamanho conhecido, até 1 GiB" }
                val offer = FileOffer(UUID.randomUUID().toString(),name.take(120),context.contentResolver.getType(uri) ?: "application/octet-stream",size)
                outgoingUri = uri; outgoingOffer = offer
                _state.update { it.copy(transfer = TransferState("Aguardando o outro usuário aceitar ${offer.name}",0,size,true)) }
                send(JSONObject().put("t","FILE_OFFER").put("id",offer.id).put("name",offer.name).put("mime",offer.mime).put("size",offer.size))
                val current = epoch
                scope.launch { delay(90_000); if (epoch == current && outgoingOffer?.id == offer.id && outgoingJob == null) cancelTransfer() }
            }.onFailure { _state.update { s -> s.copy(message = it.message ?: "Não foi possível abrir o arquivo") } }
        }
    }
    fun acceptFile(uri: Uri) {
        val offer = incomingOffer ?: return
        scope.launch {
            runCatching {
                incomingUri = uri; incomingStream = context.contentResolver.openOutputStream(uri,"w") ?: kotlin.error("Não foi possível salvar")
                incomingCount = 0; incomingDigest = MessageDigest.getInstance("SHA-256"); acceptingFiles = true
                _state.update { it.copy(offer = null, transfer = TransferState("Recebendo ${offer.name}",0,offer.size,true)) }
                send(JSONObject().put("t","FILE_ACCEPT").put("id",offer.id))
            }.onFailure { rejectFile(); _state.update { s -> s.copy(message = it.message ?: "Falha ao salvar") } }
        }
    }
    fun rejectFile() { incomingOffer?.let { send(JSONObject().put("t","FILE_REJECT").put("id",it.id)) }; clearTransfer("Arquivo recusado") }
    private fun sendFile() {
        val offer = outgoingOffer ?: return; val uri = outgoingUri ?: return; val w = bulk ?: return
        outgoingJob = scope.launch {
            try {
                val digest = MessageDigest.getInstance("SHA-256"); var count = 0L
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val buffer = ByteArray(8192)
                    while (isActive) {
                        val n = stream.read(buffer); if (n == -1) break; require(count + n <= offer.size)
                        val chunk = ByteArrayOutputStream().apply { DataOutputStream(this).apply { writeByte(2); writeUTF(offer.id); writeLong(count); write(buffer,0,n) } }.toByteArray()
                        chunkAck = CompletableDeferred(); bulkLock.withLock { w.send(chunk) }
                        require(withTimeout(15_000) { chunkAck.await() } == count + n)
                        digest.update(buffer,0,n); count += n
                        _state.update { it.copy(transfer = TransferState("Enviando ${offer.name}",count,offer.size,true)) }
                    }
                } ?: kotlin.error("Arquivo indisponível")
                require(count == offer.size) { "Arquivo mudou durante o envio" }
                send(JSONObject().put("t","FILE_END").put("id",offer.id).put("sha",Crypto.hex(digest.digest())))
            } catch (e: Exception) { if (isActive) { send(JSONObject().put("t","FILE_CANCEL")); clearTransfer("Envio interrompido: ${e.message}") } }
        }
    }
    private fun receiveChunk(input: DataInputStream) {
        require(_state.value.grants.files && acceptingFiles)
        val offer = incomingOffer ?: kotlin.error("Arquivo sem aprovação")
        require(input.readUTF() == offer.id && input.readLong() == incomingCount)
        val chunk = ByteArray(input.available()).also { input.readFully(it) }
        require(chunk.size in 1..8192 && incomingCount + chunk.size <= offer.size)
        incomingStream?.write(chunk) ?: kotlin.error("Destino fechado")
        incomingDigest!!.update(chunk); incomingCount += chunk.size
        _state.update { it.copy(transfer = TransferState("Recebendo ${offer.name}",incomingCount,offer.size,true)) }
        send(JSONObject().put("t","FILE_ACK").put("id",offer.id).put("offset",incomingCount))
    }
    private fun finishIncoming(o: JSONObject) {
        val offer = incomingOffer ?: kotlin.error("Arquivo sem aprovação")
        require(acceptingFiles && o.getString("id") == offer.id && incomingCount == offer.size)
        require(Crypto.hex(incomingDigest!!.digest()) == o.getString("sha")) { "Integridade do arquivo inválida" }
        incomingStream?.flush(); incomingStream?.close(); incomingStream = null; incomingUri = null
        send(JSONObject().put("t","FILE_DONE").put("id",offer.id)); clearTransfer("Arquivo recebido e verificado")
    }
    fun cancelTransfer() { send(JSONObject().put("t","FILE_CANCEL")); clearTransfer("Transferência cancelada") }
    private fun clearTransfer(label: String) {
        outgoingJob?.cancel(); outgoingJob = null; chunkAck.cancel()
        runCatching { incomingStream?.close() }; incomingStream = null
        // Best-effort remove incomplete user-selected documents. Providers may refuse deletion.
        incomingUri?.let { uri -> runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri) } }; incomingUri = null
        incomingOffer = null; outgoingOffer = null; outgoingUri = null; incomingDigest = null; incomingCount = 0; acceptingFiles = false
        _state.update { it.copy(offer = null,transfer = TransferState(label)) }
    }
    @Synchronized fun disconnect(message: String = "Desconectado pelo usuário", failed: Boolean = false) {
        val prior = _state.value.status; epoch++
        nativeLeaseAllowed=false;nativeLeaseAt=0
        if(Build.VERSION.SDK_INT>=28)nativePad?.stop()
        TouchService.instance?.configureSession(false)
        neutralize(); bluetooth.stop(); channel?.close(); channel = null
        runCatching { socket?.close() }; socket = null
        runCatching { bulkSocket?.close() }; bulkSocket = null
        runCatching { bulkServer?.close() }; bulkServer = null; bulk = null; bulkRequested=false
        sessionJobs?.cancel(); sessionJobs = null; watchdog?.cancel(); watchdog = null
        latestFrame.set(null); frameWaitingAck = false; inputAck = true; inputSequence = 0; frameSequence = 0
        clearTransfer("Nenhuma transferência")
        context.stopService(Intent(context, ScreenService::class.java)); context.stopService(Intent(context, SessionService::class.java))
        if (prior == LinkStatus.CONNECTED) store.log(_state.value.remoteName, message.take(150))
        localConfirmed = false; remoteConfirmed = false; externalFlow = false
        _state.update { it.copy(status = if (failed) LinkStatus.ERROR else LinkStatus.IDLE, message = message,
            grants = Grants(),code = "",localConfirmed = false,deviceInfo = DeviceInfo(),rttMs = null,bulkReady = false,
            frame = null,screenActive = false,foregroundTarget = false,history = store.history(),
            controlHealth = ControlHealth.WAITING,inputCount = 0,lastButtons = 0,
            hidConnected=false,hidReports=0,nativeControl=ControlState(),nativeInputCount=0) }
    }
}
