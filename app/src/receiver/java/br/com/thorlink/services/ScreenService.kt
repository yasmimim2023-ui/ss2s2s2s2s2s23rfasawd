package br.com.thorlink.services

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import br.com.thorlink.ThorApp
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Started only with a fresh system capture token, after session approval and local Screen button. */
class ScreenService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var previous = 0L
    private var stopping = false
    @Volatile private var capturedVisible = true
    private val repo get() = (application as ThorApp).repository
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!repo.canCapture() || projection != null) { stopSelf(); return START_NOT_STICKY }
        try {
            val token = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("token",Intent::class.java)
                else @Suppress("DEPRECATION") intent?.getParcelableExtra<Intent>("token")
            require(token != null)
            val notification = SessionNotification.create(this,"Tela sendo compartilhada por sua autorização. Nenhum áudio é capturado.")
            if (Build.VERSION.SDK_INT >= 29) startForeground(2,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(2,notification)
            val manager = getSystemService(MediaProjectionManager::class.java)
            projection = manager.getMediaProjection(Activity.RESULT_OK,token)
            thread = HandlerThread("consented-screen").apply { start() }
            val handler = Handler(thread!!.looper)
            val metrics = resources.displayMetrics
            val scale = 480f / maxOf(metrics.widthPixels,metrics.heightPixels)
            val width = (metrics.widthPixels*scale).roundToInt().coerceAtLeast(2)
            val height = (metrics.heightPixels*scale).roundToInt().coerceAtLeast(2)
            reader = ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
            projection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
                override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                    // Android single-app sharing pauses when its selected app is not visible.
                    capturedVisible = isVisible
                    if (!isVisible) repo.screenPaused()
                }
            },handler)
            reader!!.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                image.use {
                    if (!repo.canCapture() || !capturedVisible) return@use
                    val now = SystemClock.elapsedRealtime(); if (now-previous < 125) return@use; previous = now
                    val plane = it.planes[0]; val paddedWidth = width + (plane.rowStride-plane.pixelStride*width)/plane.pixelStride
                    val padded = Bitmap.createBitmap(paddedWidth,height,Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val bitmap = Bitmap.createBitmap(padded,0,0,width,height)
                    val bytes = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG,35,bytes)
                    repo.submitFrame(bytes.toByteArray()); if (bitmap !== padded) bitmap.recycle(); padded.recycle()
                }
            },handler)
            display = projection!!.createVirtualDisplay("ThorLink consented screen",width,height,metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
            repo.screenStarted()
        } catch (e: Exception) { repo.screenStopped(); stopSelf(); repo.error("Não foi possível compartilhar a tela: ${e.message}") }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        if (!stopping) {
            stopping = true; reader?.setOnImageAvailableListener(null,null); display?.release(); reader?.close()
            projection?.stop(); thread?.quitSafely(); repo.screenStopped()
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
