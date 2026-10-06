package br.com.thorlink

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import br.com.thorlink.services.ScreenService
import br.com.thorlink.ui.*
import br.com.thorlink.domain.FileOffer
import br.com.thorlink.domain.*

class SaveSelectedDocument : ActivityResultContract<FileOffer,android.net.Uri?>() {
    override fun createIntent(context: android.content.Context,input: FileOffer) = Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE).setType(input.mime).putExtra(Intent.EXTRA_TITLE,input.name)
    override fun parseResult(resultCode: Int,intent: Intent?): android.net.Uri? = if(resultCode==Activity.RESULT_OK)intent?.data else null
}

class MainActivity : ComponentActivity() {
    private val vm: LinkViewModel by viewModels()
    private var afterPermission: (() -> Unit)? = null
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action = afterPermission; afterPermission = null
        if (requiredPermissions().all { p -> ContextCompat.checkSelfPermission(this,p) == PackageManager.PERMISSION_GRANTED }) action?.invoke()
        else vm.repo.error("Permissão negada. Abra as configurações do aplicativo para autorizar Dispositivos próximos${if (Build.VERSION.SDK_INT < 31) " e Localização para a busca Bluetooth" else ""}.")
    }
    private val enable = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.repo.externalFlow = false; if (vm.repo.bluetooth.adapter?.isEnabled == true) vm.repo.refresh()
    }
    private val discoverable = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.repo.externalFlow = false }
    private val selectFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.repo.externalFlow = false; if (uri != null) vm.selectFile(uri)
    }
    private val saveFile = registerForActivityResult(SaveSelectedDocument()) { uri ->
        vm.repo.externalFlow = false; if (uri != null) vm.repo.acceptFile(uri) else vm.repo.rejectFile()
    }
    private val capture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.repo.externalFlow = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null && vm.repo.canCapture()) {
            ContextCompat.startForegroundService(this,Intent(this,ScreenService::class.java).putExtra("token",result.data))
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            ThorTheme {
                ThorScreen(vm,
                    onScan = { permitted { vm.repo.scan() } },
                    onRefresh = { permitted { vm.repo.refresh() } },
                    onPair = { peer -> permitted { vm.repo.externalFlow = true; vm.repo.pair(peer) } },
                    onConnect = { peer -> permitted { vm.repo.connect(peer) } },
                    onListen = { permitted { vm.repo.listen() } },
                    onBluetooth = { permitted { vm.repo.externalFlow = true; enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } },
                    onDiscoverable = { permitted {
                        vm.repo.externalFlow = true
                        discoverable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120))
                    } },
                    onSelectFile = { vm.repo.externalFlow = true; selectFile.launch(arrayOf("*/*")) },
                    onSaveFile = { offer -> vm.repo.externalFlow = true; saveFile.launch(offer) },
                    onCapture = {
                        if (vm.repo.canCapture()) {
                            vm.repo.externalFlow = true
                            capture.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
                        }
                    },
                    onStopCapture = { stopService(Intent(this,ScreenService::class.java)) },
                    onAccessibility = {
                        vm.repo.externalFlow = true
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onLaunchTarget = {
                        val pkg = vm.state.value.targetPackage
                        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                    },
                    onAppSettings = { vm.repo.externalFlow = true; startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName"))) },
                    onNativeKey = ::handleNativeKey
                )
            }
        }
    }
    private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        buildList {
            add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun permitted(action: () -> Unit) {
        if (requiredPermissions().all { ContextCompat.checkSelfPermission(this,it) == PackageManager.PERMISSION_GRANTED }) action()
        else { afterPermission = action; permissions.launch(requiredPermissions()) }
    }
    override fun onResume() { super.onResume(); vm.repo.externalFlow = false; vm.repo.visible(true); vm.repo.foregroundPackage(packageName) }
    private fun handleNativeKey(event:KeyEvent):Boolean {
        if(BuildConfig.IS_RECEIVER && vm.state.value.status==LinkStatus.CONNECTED && vm.state.value.grants.nativeGamepad &&
            (event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK))) {
            val bit=when(event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A->Buttons.A;KeyEvent.KEYCODE_BUTTON_B->Buttons.B
                KeyEvent.KEYCODE_BUTTON_X->Buttons.X;KeyEvent.KEYCODE_BUTTON_Y->Buttons.Y
                KeyEvent.KEYCODE_BUTTON_L1->Buttons.L1;KeyEvent.KEYCODE_BUTTON_R1->Buttons.R1
                KeyEvent.KEYCODE_BUTTON_L2->Buttons.L2;KeyEvent.KEYCODE_BUTTON_R2->Buttons.R2
                KeyEvent.KEYCODE_BUTTON_SELECT->Buttons.SELECT;KeyEvent.KEYCODE_BUTTON_START->Buttons.START
                KeyEvent.KEYCODE_BUTTON_THUMBL->Buttons.L3;KeyEvent.KEYCODE_BUTTON_THUMBR->Buttons.R3
                else->0
            }
            if(bit!=0) {
                val c=vm.state.value.nativeControl
                if(event.action==KeyEvent.ACTION_DOWN || event.action==KeyEvent.ACTION_UP)
                    vm.repo.nativeInput(c.copy(buttons=if(event.action==KeyEvent.ACTION_DOWN)c.buttons or bit else c.buttons and bit.inv()))
                return true
            }
        }
        return false
    }
    override fun onKeyDown(keyCode:Int,event:KeyEvent)=handleNativeKey(event)||super.onKeyDown(keyCode,event)
    override fun onKeyUp(keyCode:Int,event:KeyEvent)=handleNativeKey(event)||super.onKeyUp(keyCode,event)
    override fun dispatchGenericMotionEvent(event:MotionEvent):Boolean {
        if(BuildConfig.IS_RECEIVER && vm.state.value.status==LinkStatus.CONNECTED && vm.state.value.grants.nativeGamepad &&
            event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action==MotionEvent.ACTION_MOVE) {
            val c=vm.state.value.nativeControl
            var bits=c.buttons and 0xf0.inv()
            val hx=event.getAxisValue(MotionEvent.AXIS_HAT_X);val hy=event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            if(hx<-.5f)bits=bits or Buttons.LEFT else if(hx>.5f)bits=bits or Buttons.RIGHT
            if(hy<-.5f)bits=bits or Buttons.UP else if(hy>.5f)bits=bits or Buttons.DOWN
            val lt=maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),event.getAxisValue(MotionEvent.AXIS_BRAKE))
            val rt=maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),event.getAxisValue(MotionEvent.AXIS_GAS))
            bits=if(lt>.5f)bits or Buttons.L2 else bits and Buttons.L2.inv()
            bits=if(rt>.5f)bits or Buttons.R2 else bits and Buttons.R2.inv()
            vm.repo.nativeInput(ControlState(event.getAxisValue(MotionEvent.AXIS_X),event.getAxisValue(MotionEvent.AXIS_Y),
                event.getAxisValue(MotionEvent.AXIS_Z),event.getAxisValue(MotionEvent.AXIS_RZ),bits))
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
    override fun onStop() { vm.repo.visible(false); super.onStop() }
    override fun onDestroy() { if (isFinishing) vm.repo.disconnect("Aplicativo fechado"); super.onDestroy() }
}
