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
        com.radiocoche.telefono.ui.CarLights.start(applicationContext) { com.radiocoche.telefono.ui.DayNight.apply() }
        com.radiocoche.telefono.ui.DayNight.apply()
        overlay = CallOverlay(this)
        PhoneLink.start(this)
        PhoneLink.onStateChange = { s -> onCall(s) }
    }

    private lateinit var overlay: CallOverlay
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var silenced = false

    /**
     * Con llamada (sonando, marcando o en curso): se calla lo que suene (lo hace el launcher, que sabe qué fuente es)
     * y se pone nuestra pantalla de llamada encima de todo. Se vuelve a poner encima poco después,
     * por si la ventanita de fábrica aparece más tarde.
     */
    private fun onCall(s: com.radiocoche.telefono.bt.CallState) {
        val inCall = s != com.radiocoche.telefono.bt.CallState.IDLE
        if (inCall != silenced) {
            silenced = inCall
            runCatching {
                sendBroadcast(Intent("com.radiocoche.launcher.SILENCE").setPackage("com.radiocoche.launcher").putExtra("on", inCall))
            }
        }
        handler.removeCallbacksAndMessages(null)
        if (!inCall) {
            overlay.hide()
            return
        }
        if (!overlay.canShow()) {
            if (s == com.radiocoche.telefono.bt.CallState.INCOMING) showCallScreen()
            return
        }
        overlay.show()
        for (ms in longArrayOf(400, 1200, 2500)) handler.postDelayed({ if (PhoneLink.state != com.radiocoche.telefono.bt.CallState.IDLE) overlay.show() }, ms)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlay.hide()
        super.onDestroy()
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
