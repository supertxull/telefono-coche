package com.radiocoche.telefono

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import com.radiocoche.telefono.bt.PhoneLink

/**
 * Servicio siempre en marcha: escucha las llamadas para apuntarlas en Recientes
 * y abre la pantalla de llamada entrante de esta app.
 */
class PhoneService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, notification())
        PhoneLink.start(this)
        PhoneLink.onIncoming = { showCallScreen() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PhoneLink.query()
        return START_STICKY
    }

    private fun showCallScreen() {
        // En Android 10 abrir una pantalla desde segundo plano necesita «Mostrar sobre otras apps».
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this) && !MainActivity.visible) return
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Teléfono", NotificationManager.IMPORTANCE_MIN))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0),
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("Teléfono")
            .setContentText("Atento a las llamadas")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "telefono"

        fun start(ctx: Context) {
            val i = Intent(ctx, PhoneService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            }
        }
    }
}

/** Arranca el servicio al encender la unidad. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PhoneService.start(context)
    }
}
