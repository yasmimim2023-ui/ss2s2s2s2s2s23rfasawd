package br.com.thorlink.transport

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.annotation.RequiresApi
import br.com.thorlink.domain.*
import java.util.concurrent.Executor

/**
 * Public Bluetooth HID Device API. Input goes directly to Android's HID host, not dispatchGesture.
 * The RFCOMM session remains the mutually authenticated consent/lease channel. Only that bonded
 * peer receives reports. The sender cannot read any contents or inject keyboard/mouse commands.
 */
@RequiresApi(28)
@SuppressLint("MissingPermission")
class HidGamepad(private val context:Context,private val allowed:()->Boolean,
    private val onStatus:(ControlHealth,Boolean)->Unit,private val onReport:()->Unit) {
    private val adapter=context.getSystemService(BluetoothManager::class.java)?.adapter
    private val worker=HandlerThread("ThorLink-HID").apply{start()}
    private val handler=Handler(worker.looper)
    private val executor=Executor { handler.post(it) }
    private val buffer=ControlInputBuffer()
    @Volatile private var generation=0L
    @Volatile private var armed=false
    private var proxy:BluetoothHidDevice?=null
    private var host:BluetoothDevice?=null
    private var wanted=false
    private var linked=false
    private var lastReport=HidReport.encode(ControlState())
    private var sentAt=0L

    fun offer(c:ControlState) { if(!buffer.offer(c,SystemClock.elapsedRealtime())) onStatus(ControlHealth.INPUT_OVERFLOW,linked) }
    fun neutralize() { buffer.reset(); handler.post { if(linked) send(HidReport.encode(ControlState())) } }
    fun start(address:String) {
        val token=++generation;armed=false
        buffer.reset()
        handler.post {
            if(token!=generation)return@post
            closeProfile(); wanted=true;armed=true
            try {
                val a=adapter ?: return@post fail(ControlHealth.HID_UNSUPPORTED)
                val peer=a.getRemoteDevice(address)
                if(peer.bondState!=BluetoothDevice.BOND_BONDED) return@post fail(ControlHealth.HID_ERROR)
                host=peer;onStatus(ControlHealth.HID_CONNECTING,false)
                val requested=a.getProfileProxy(context,object:BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile:Int,service:BluetoothProfile) { handler.post {
                        if(token!=generation || !wanted) { a.closeProfileProxy(BluetoothProfile.HID_DEVICE,service);return@post }
                        val hid=service as? BluetoothHidDevice ?: return@post fail(ControlHealth.HID_UNSUPPORTED)
                        proxy=hid
                        val sdp=BluetoothHidDeviceAppSdpSettings("ThorLink Gamepad","Controle autorizado", "ThorLink",
                            BluetoothHidDevice.SUBCLASS2_GAMEPAD,HidReport.descriptor)
                        if(!runCatching { hid.registerApp(sdp,null,null,executor,callback(token)) }.getOrDefault(false))
                            fail(ControlHealth.HID_ERROR)
                    } }
                    override fun onServiceDisconnected(profile:Int) { handler.post {
                        if(token==generation && wanted) fail(ControlHealth.HID_ERROR)
                    } }
                },BluetoothProfile.HID_DEVICE)
                if(!requested) fail(ControlHealth.HID_UNSUPPORTED)
                handler.postDelayed({ if(token==generation && wanted && !linked)
                    fail(if(proxy==null)ControlHealth.HID_UNSUPPORTED else ControlHealth.HID_ERROR) },20_000)
            } catch (_:Exception) { fail(ControlHealth.HID_ERROR) }
        }
    }
    fun stop(paused:Boolean=false) {
        armed=false;generation++; buffer.reset()
        handler.post { closeProfile(); if(paused) onStatus(ControlHealth.HID_PAUSED,false) }
    }
    private fun callback(token:Long)=object:BluetoothHidDevice.Callback() {
        private fun valid()=token==generation && wanted
        private fun peer(device:BluetoothDevice?)=device!=null && device.address==host?.address && device.bondState==BluetoothDevice.BOND_BONDED
        override fun onAppStatusChanged(pluggedDevice:BluetoothDevice?,registered:Boolean) {
            if(!valid()) return
            if(!registered) { fail(ControlHealth.HID_ERROR);return }
            if(pluggedDevice!=null && !peer(pluggedDevice)) runCatching{proxy?.disconnect(pluggedDevice)}
            val target=host ?: return
            if(!runCatching{proxy?.connect(target)==true}.getOrDefault(false)) fail(ControlHealth.HID_ERROR)
        }
        override fun onConnectionStateChanged(device:BluetoothDevice,state:Int) {
            if(!valid()) return
            if(!peer(device)) { runCatching{proxy?.disconnect(device)};return }
            when(state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    linked=true;buffer.reset();lastReport=HidReport.encode(ControlState());sentAt=0
                    onStatus(ControlHealth.HID_CONNECTED,true);handler.post(pump)
                }
                BluetoothProfile.STATE_DISCONNECTED -> fail(ControlHealth.HID_ERROR)
            }
        }
        override fun onGetReport(device:BluetoothDevice,type:Byte,id:Byte,bufferSize:Int) {
            if(!valid() || !peer(device)) return
            runCatching {
                if(type==BluetoothHidDevice.REPORT_TYPE_INPUT && id.toInt()==HidReport.ID && bufferSize in 0..64)
                    proxy?.replyReport(device,type,id,if(allowed())lastReport else HidReport.encode(ControlState()))
                else proxy?.reportError(device,BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
            }.onFailure{fail(ControlHealth.HID_ERROR)}
        }
        override fun onSetReport(device:BluetoothDevice,type:Byte,id:Byte,data:ByteArray) {
            if(valid() && peer(device)) runCatching{proxy?.reportError(device,BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)}
        }
        override fun onSetProtocol(device:BluetoothDevice,protocol:Byte) {
            if(valid() && peer(device) && protocol!=BluetoothHidDevice.PROTOCOL_REPORT_MODE) fail(ControlHealth.HID_ERROR)
        }
        override fun onVirtualCableUnplug(device:BluetoothDevice) { if(valid() && peer(device)) fail(ControlHealth.HID_ERROR) }
    }
    private val pump=object:Runnable {
        override fun run() {
            if(!wanted || !linked) return
            val canSend=allowed()
            val c=if(canSend)buffer.next(SystemClock.elapsedRealtime()) else {buffer.reset();ControlState()}
            val report=HidReport.encode(c)
            if(!report.contentEquals(lastReport) || SystemClock.elapsedRealtime()-sentAt>=100) send(report)
            if(!wanted || !linked)return // A failed send closes the profile; never overwrite that error with "connected".
            if(!canSend) onStatus(ControlHealth.HID_LOCKED,true) else onStatus(ControlHealth.HID_CONNECTED,true)
            if(wanted && linked) handler.postDelayed(this,8)
        }
    }
    private fun send(report:ByteArray) {
        val target=host ?: return
        val safe=if(armed && allowed())report else HidReport.encode(ControlState())
        if(!runCatching{proxy?.sendReport(target,HidReport.ID,safe)==true}.getOrDefault(false)) {fail(ControlHealth.HID_ERROR);return}
        lastReport=safe;sentAt=SystemClock.elapsedRealtime();onReport()
    }
    private fun fail(status:ControlHealth) { generation++;closeProfile();onStatus(status,false) }
    private fun closeProfile() {
        wanted=false;armed=false;handler.removeCallbacks(pump);buffer.reset()
        host?.let { target -> runCatching{proxy?.sendReport(target,HidReport.ID,HidReport.encode(ControlState()))};runCatching{proxy?.disconnect(target)} }
        linked=false
        runCatching{proxy?.unregisterApp()}
        proxy?.let { runCatching{adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE,it)} }
        proxy=null;host=null;lastReport=HidReport.encode(ControlState());sentAt=0
    }
}
