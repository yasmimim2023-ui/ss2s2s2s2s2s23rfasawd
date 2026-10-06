package br.com.thorlink.domain

enum class LinkStatus(val label: String) {
    IDLE("Desconectado"), DISCOVERING("Procurando"), LISTENING("Aguardando controlador"),
    CONNECTING("Conectando"), VERIFYING("Confirme o código"), CONNECTED("Conectado"), ERROR("Erro")
}
data class Peer(val address: String, val name: String, val bonded: Boolean)
data class Grants(val name: Boolean = false, val model: Boolean = false, val battery: Boolean = false,
    val files: Boolean = false, val screen: Boolean = false, val controls: Boolean = false,
    val nativeGamepad: Boolean = false)
enum class ControlMode { HID, GESTURES }
data class AuthorizedPeer(val address: String, val name: String, val fingerprint: String)
data class HistoryEntry(val time: Long, val name: String, val result: String)
data class DeviceInfo(val name: String? = null, val model: String? = null, val battery: Int? = null)
data class ControlState(val lx: Float = 0f, val ly: Float = 0f, val rx: Float = 0f, val ry: Float = 0f, val buttons: Int = 0)
enum class ControlHealth(val label: String) {
    WAITING("Aguardando diagnóstico do receptor"),
    TEST_MODE("Modo de teste: comandos recebidos, sem gestos externos"),
    ACCESSIBILITY_OFF("Ative ThorLink na Acessibilidade do receptor"),
    OPEN_TARGET("Abra o app escolhido no receptor para liberar gestos"),
    READY("Gestos prontos • pressione um controle"),
    LOCKED("Receptor bloqueado: desbloqueie para controlar"),
    INPUT_STALE("Comandos pausados: aguardando o controlador"),
    ANDROID_COMPLETED("Android concluiu o gesto • confira o mapeamento"),
    ANDROID_CANCELLED("Android cancelou o gesto: evite tocar no receptor durante o controle"),
    ANDROID_REJECTED("Android recusou o gesto: revise a Acessibilidade no receptor"),
    ANDROID_ERROR("Falha ao criar gesto: revise o mapeamento no receptor"),
    INPUT_OVERFLOW("Comandos atrasados descartados: solte os controles e tente novamente"),
    HID_WAITING("Gamepad HID autorizado • aguardando iniciar"),
    HID_CONNECTING("Conectando gamepad Bluetooth HID"),
    HID_CONNECTED("Bluetooth HID conectado • teste os botões no receptor"),
    HID_UNSUPPORTED("Perfil HID indisponível neste celular controlador"),
    HID_ERROR("HID não conectou • abra Bluetooth no receptor e tente novamente"),
    HID_PAUSED("HID pausado • toque em Iniciar gamepad Bluetooth no Controle"),
    HID_LOCKED("Gamepad pausado: receptor bloqueado ou autorização sem resposta")
}
object Buttons {
    const val A = 1; const val B = 2; const val X = 4; const val Y = 8
    const val UP = 16; const val DOWN = 32; const val LEFT = 64; const val RIGHT = 128
    const val L1=256; const val R1=512; const val L2=1024; const val R2=2048
    const val SELECT=4096; const val START=8192; const val L3=16384; const val R3=32768
}
data class FileOffer(val id: String, val name: String, val mime: String, val size: Long)
data class TransferState(val label: String = "Nenhuma transferência", val done: Long = 0, val total: Long = 0, val active: Boolean = false)
data class Mapping(val stickX: Float = .18f, val stickY: Float = .72f, val radius: Float = .12f,
    val aX: Float = .86f, val aY: Float = .72f, val bX: Float = .76f, val bY: Float = .82f,
    val xX: Float = .76f, val xY: Float = .60f, val yX: Float = .66f, val yY: Float = .72f,
    val rightX: Float = .60f, val rightY: Float = .40f)
data class UiState(
    val status: LinkStatus = LinkStatus.IDLE, val message: String = "Somente com autorização dos dois aparelhos",
    val peers: List<Peer> = emptyList(), val remoteName: String = "", val code: String = "",
    val fingerprint: String = "", val localConfirmed: Boolean = false,
    val grants: Grants = Grants(), val deviceInfo: DeviceInfo = DeviceInfo(),
    val authorized: List<AuthorizedPeer> = emptyList(), val history: List<HistoryEntry> = emptyList(),
    val control: ControlState = ControlState(), val rttMs: Int? = null, val bulkReady: Boolean = false,
    val frame: android.graphics.Bitmap? = null, val screenActive: Boolean = false,
    val offer: FileOffer? = null, val transfer: TransferState = TransferState(),
    val targetPackage: String = "", val targetLabel: String = "Modo de teste", val mapping: Mapping = Mapping(),
    val touchAvailable: Boolean = false, val foregroundTarget: Boolean = false,
    val frameAgeMs: Long? = null,
    // Diagnostics remain in memory and are shared only while controls are explicitly authorized.
    val controlHealth: ControlHealth = ControlHealth.WAITING,
    val inputCount: Long = 0, val lastButtons: Int = 0,
    val controlMode: ControlMode = ControlMode.HID,
    val hidConnected: Boolean = false, val hidReports: Long = 0,
    val nativeControl: ControlState = ControlState(), val nativeInputCount: Long = 0
)
