package br.com.thorlink.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Compile-time placeholder only. The controller manifest does not expose or start screen capture. */
class ScreenService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
