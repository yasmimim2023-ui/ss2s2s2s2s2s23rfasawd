package br.com.thorlink.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import br.com.thorlink.ThorApp
import br.com.thorlink.domain.*
import kotlin.math.*

/** Remote assistive input. No window contents, global navigation, keyboard hooks, shell, or root. */
class TouchService : AccessibilityService() {
    companion object { @Volatile var instance: TouchService? = null; private set }
    private val main = Handler(Looper.getMainLooper())
    private val repo get() = (application as ThorApp).repository
    private var strokes = mapOf<String,GestureDescription.StrokeDescription>()
    private var points = mapOf<String,PointF>()
    private var busy = false
    private var generation = 0L
    private var stopped = false
    private var releaseRequested = false
    override fun onServiceConnected() {
        instance = this; stopped = false; repo.touchAvailable(true)
        configureSession(repo.state.value.status==LinkStatus.CONNECTED && repo.state.value.grants.controls && !repo.state.value.grants.nativeGamepad)
        main.post(pump)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (repo.state.value.status!=LinkStatus.CONNECTED || !repo.state.value.grants.controls || repo.state.value.grants.nativeGamepad) return
        // Only package identity from a window-state event. Never request the source/root node.
        repo.foregroundPackage(event?.packageName?.toString().orEmpty())
    }
    override fun onInterrupt() { release() }
    override fun onDestroy() {
        // Invalidate callbacks from gestures that belonged to this service instance.
        releaseRequested = true
        if (!busy) tick()
        stopped = true; generation++; busy = false
        main.removeCallbacksAndMessages(null); instance = null; repo.touchAvailable(false); super.onDestroy()
    }
    fun configureSession(active:Boolean) {
        main.post {
            runCatching {
                val info=serviceInfo
                info.eventTypes=if(active)AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
                serviceInfo=info
            }.onFailure { repo.reportControlHealth(ControlHealth.ANDROID_ERROR) }
            if(!active)release()
        }
    }
    fun release() {
        main.post {
            // Finish continued strokes at their current coordinates, without a synthetic tap elsewhere.
            releaseRequested = true
            if (!busy) tick()
        }
    }
    private val pump = object : Runnable {
        override fun run() {
            if (stopped) return
            if (!busy) tick()
            main.postDelayed(this,if(repo.state.value.status==LinkStatus.CONNECTED && repo.state.value.grants.controls && !repo.state.value.grants.nativeGamepad)8 else 250)
        }
    }
    private fun tick() {
        repo.refreshControlHealth()
        val allowed = !releaseRequested && repo.touchAllowed()
        releaseRequested = false
        val desired = if (allowed) positions(repo.nextGestureControl(),repo.state.value.mapping) else emptyMap()
        if (desired.isEmpty() && strokes.isEmpty()) return
        try {
        val builder = GestureDescription.Builder()
        val nextStrokes = mutableMapOf<String,GestureDescription.StrokeDescription>()
        val nextPoints = mutableMapOf<String,PointF>()
        for (id in (strokes.keys + desired.keys).distinct()) {
            val previousPoint = points[id]
            val target = desired[id] ?: previousPoint ?: continue
            val dm = resources.displayMetrics
            val mapping = repo.state.value.mapping
            val anchor = when(id) {
                "L" -> PointF(mapping.stickX*dm.widthPixels,mapping.stickY*dm.heightPixels)
                "R" -> PointF(mapping.rightX*dm.widthPixels,mapping.rightY*dm.heightPixels)
                else -> target
            }
            val from = previousPoint ?: anchor
            val path = Path().apply { moveTo(from.x,from.y); if (from != target) lineTo(target.x,target.y) }
            val keep = id in desired
            val stroke = strokes[id]?.continueStroke(path,0,32,keep)
                ?: GestureDescription.StrokeDescription(path,0,32,keep)
            builder.addStroke(stroke)
            if (keep) { nextStrokes[id] = stroke; nextPoints[id] = target }
        }
        val version = generation; busy = true
        val accepted = dispatchGesture(builder.build(),object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (version != generation) return
                strokes = nextStrokes; points = nextPoints; busy = false
                if (desired.isNotEmpty() && repo.touchAllowed()) repo.reportControlHealth(ControlHealth.ANDROID_COMPLETED)
                else repo.refreshControlHealth()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (version != generation) return
                strokes = emptyMap(); points = emptyMap(); busy = false
                if (repo.touchAllowed()) repo.reportControlHealth(ControlHealth.ANDROID_CANCELLED)
                else repo.refreshControlHealth()
            }
        },main)
        if (!accepted) {
            busy = false; strokes = emptyMap(); points = emptyMap()
            if (repo.touchAllowed()) repo.reportControlHealth(ControlHealth.ANDROID_REJECTED)
        }
        } catch (_: Exception) {
            busy = false; strokes = emptyMap(); points = emptyMap()
            if (repo.touchAllowed()) repo.reportControlHealth(ControlHealth.ANDROID_ERROR)
        }
    }
    private fun positions(c: ControlState,m: Mapping): Map<String,PointF> {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat(); val h = dm.heightPixels.toFloat(); val radius = min(w,h)*m.radius
        val result = mutableMapOf<String,PointF>()
        var lx = c.lx; var ly = c.ly
        if (c.buttons and Buttons.LEFT != 0) lx = -1f
        if (c.buttons and Buttons.RIGHT != 0) lx = 1f
        if (c.buttons and Buttons.UP != 0) ly = -1f
        if (c.buttons and Buttons.DOWN != 0) ly = 1f
        if (hypot(lx,ly) > .12f) result["L"] = PointF(m.stickX*w+lx*radius,m.stickY*h+ly*radius)
        if (hypot(c.rx,c.ry) > .12f) result["R"] = PointF(m.rightX*w+c.rx*radius,m.rightY*h+c.ry*radius)
        listOf(Triple(Buttons.A,m.aX,m.aY),Triple(Buttons.B,m.bX,m.bY),Triple(Buttons.X,m.xX,m.xY),Triple(Buttons.Y,m.yX,m.yY)).forEachIndexed { i,p ->
            if (c.buttons and p.first != 0) result["B$i"] = PointF(p.second*w,p.third*h)
        }
        return result.mapValues { (_, p) -> PointF(p.x.coerceIn(1f,w-2),p.y.coerceIn(1f,h-2)) }
    }
}
