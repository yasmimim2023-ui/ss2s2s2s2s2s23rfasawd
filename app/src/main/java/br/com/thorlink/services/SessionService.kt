package br.com.thorlink.services

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import br.com.thorlink.*

object SessionNotification {
    const val CHANNEL = "authorized_session"
    fun create(service: Service, text: String): Notification {
        service.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"Sessão Bluetooth autorizada",NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(service,0,Intent(service,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(service,1,Intent(service,SessionService::class.java).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(service,CHANNEL).setSmallIcon(R.drawable.ic_thorlink)
            .setContentTitle("ThorLink • Sessão autorizada").setContentText(text).setOngoing(true).setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Desconectar",stop).build()
    }
}
class SessionService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = (application as ThorApp).repository
        if (intent?.action == "STOP") { repo.disconnect(); stopSelf(); return START_NOT_STICKY }
        if (repo.state.value.status != br.com.thorlink.domain.LinkStatus.CONNECTED) { stopSelf(); return START_NOT_STICKY }
        val notification = SessionNotification.create(this,"Bluetooth ativo. Toque em Desconectar para encerrar.")
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(1,notification)
        return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) { (application as ThorApp).repository.disconnect("Aplicativo fechado"); stopSelf() }
    override fun onBind(intent: Intent?): IBinder? = null
}
