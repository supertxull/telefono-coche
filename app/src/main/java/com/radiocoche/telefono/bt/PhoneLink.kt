package com.radiocoche.telefono.bt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** Estado de la llamada según la app de fábrica (campo "state"). */
enum class CallState { IDLE, INCOMING, ACTIVE, DIALING }

/**
 * Teléfono por Bluetooth. Lo hace la app de fábrica `com.tw.bt`; aquí se ve y se manda.
 *
 * Estado (avisos de com.tw.bt, también sin pedirlos):
 *  - `com.unisound.intent.action.SEND_CALL_INFO`: state (0 colgada · 1 entrante sonando · 2 en curso · 3/4 marcando),
 *    callName, callNumber. Se pide con `GET_CALL_INFO`.
 *  - `action.bt.AIOS_INCOMING_RINGING` (name, number), `..._OFFHOOK`, `..._IDLE`, `AIOS_OUTGOING_*`.
 *  - Nombre del móvil: `GET_PAIR_INFO` → `SEND_PAIR_INFO` (pairName). Conexión: `com.android.bt.connected` / `disconnected`.
 * Órdenes: [XtCommands] (llamar, contestar, colgar). Si no responde, llamar abre la app de fábrica con tel:.
 */
object PhoneLink {

    var state by mutableStateOf(CallState.IDLE)
        private set
    var name by mutableStateOf("")
        private set
    var number by mutableStateOf("")
        private set
    /** Momento (elapsedRealtime) en que empezó la conversación; 0 si no hay. */
    var activeSince by mutableLongStateOf(0L)
        private set
    var phoneName by mutableStateOf("")
        private set

    /** Se llama al empezar a sonar una llamada entrante. */
    var onIncoming: (() -> Unit)? = null

    private var app: Context? = null
    private var xt: XtCommands? = null
    private var started = false

    // Para apuntar la llamada en Recientes al terminar.
    private var wasIncoming = false
    private var wasAnswered = false
    private var callName = ""
    private var callNumber = ""

    fun start(context: Context) {
        if (started) return
        started = true
        val ctx = context.applicationContext
        app = ctx
        xt = XtCommands(ctx).also { it.bind() }
        Recents.load(ctx)
        val f = IntentFilter().apply {
            addAction(SEND_CALL_INFO)
            addAction(SEND_CALL_INFO2)
            addAction(SEND_PAIR)
            addAction(BT_CONNECTED)
            addAction(BT_DISCONNECTED)
            for (a in AIOS) addAction(a)
        }
        ContextCompat.registerReceiver(ctx, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        query()
    }

    fun query() {
        send(Intent(GET_CALL_INFO))
        send(Intent(GET_PAIR))
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                SEND_CALL_INFO, SEND_CALL_INFO2 -> update(
                    when (i.getIntExtra("state", 0)) {
                        1 -> CallState.INCOMING
                        2 -> CallState.ACTIVE
                        3, 4 -> CallState.DIALING
                        else -> CallState.IDLE
                    },
                    i.getStringExtra("callName"), i.getStringExtra("callNumber"),
                )
                "action.bt.AIOS_INCOMING_RINGING" -> update(CallState.INCOMING, i.getStringExtra("name"), i.getStringExtra("number"))
                "action.bt.AIOS_OUTGOING_RINGING" -> update(CallState.DIALING, null, null)
                "action.bt.AIOS_INCOMING_OFFHOOK", "action.bt.AIOS_OUTGOING_OFFHOOK" -> update(CallState.ACTIVE, null, null)
                "action.bt.AIOS_INCOMING_IDLE", "action.bt.AIOS_OUTGOING_IDLE" -> update(CallState.IDLE, null, null)
                SEND_PAIR -> phoneName = i.getStringExtra("pairName")?.trim().orEmpty()
                BT_CONNECTED -> send(Intent(GET_PAIR))
                BT_DISCONNECTED -> phoneName = ""
            }
        }
    }

    private fun update(s: CallState, n: String?, num: String?) {
        if (!n.isNullOrBlank()) name = n.trim()
        if (!num.isNullOrBlank()) number = num.trim()
        val before = state
        if (s == before) return
        when (s) {
            CallState.INCOMING -> {
                wasIncoming = true
                wasAnswered = false
            }
            CallState.DIALING -> {
                if (before == CallState.IDLE) {
                    wasIncoming = false
                    wasAnswered = false
                }
            }
            CallState.ACTIVE -> {
                wasAnswered = true
                activeSince = SystemClock.elapsedRealtime()
            }
            CallState.IDLE -> {}
        }
        if (s != CallState.IDLE) {
            callName = name
            callNumber = number
        }
        state = s
        if (s == CallState.INCOMING) onIncoming?.invoke()
        if (s == CallState.IDLE) {
            // Termina la llamada: se apunta en Recientes.
            val kind = when {
                wasIncoming && wasAnswered -> Recents.Kind.IN
                wasIncoming -> Recents.Kind.MISSED
                else -> Recents.Kind.OUT
            }
            val secs = if (wasAnswered && activeSince > 0) ((SystemClock.elapsedRealtime() - activeSince) / 1000).toInt() else 0
            app?.let { Recents.add(it, kind, callName, callNumber, secs) }
            activeSince = 0
            name = ""
            number = ""
        }
    }

    /** Llama a un número. [shownName] es lo que se ve mientras marca. */
    fun call(numberToCall: String, shownName: String) {
        val clean = numberToCall.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (clean.isEmpty()) return
        name = shownName
        number = numberToCall
        callName = shownName
        callNumber = numberToCall
        wasIncoming = false
        wasAnswered = false
        state = CallState.DIALING
        val ok = xt?.call(clean) == true
        if (!ok) {
            // Sin el servicio del sistema: que lo haga la app de fábrica.
            val ctx = app ?: return
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(clean)))
                        .setPackage(FACTORY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    fun accept() {
        if (xt?.accept() != true) openFactory()
    }

    fun hangUp() {
        if (xt?.hangUp() != true) openFactory()
        // Si la unidad no avisa, que la pantalla no se quede en llamada.
        if (state == CallState.DIALING) update(CallState.IDLE, null, null)
    }

    fun openFactory() {
        val ctx = app ?: return
        val i = ctx.packageManager.getLaunchIntentForPackage(FACTORY) ?: return
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun send(i: Intent) {
        runCatching { app?.sendBroadcast(i.setPackage(FACTORY)) }
    }

    const val FACTORY = "com.tw.bt"
    private const val GET_CALL_INFO = "com.unisound.intent.action.GET_CALL_INFO"
    private const val SEND_CALL_INFO = "com.unisound.intent.action.SEND_CALL_INFO"
    private const val SEND_CALL_INFO2 = "com.unisound.intent.action.SEND_CALL_INFO2"
    private const val GET_PAIR = "com.unisound.intent.action.GET_PAIR_INFO"
    private const val SEND_PAIR = "com.unisound.intent.action.SEND_PAIR_INFO"
    private const val BT_CONNECTED = "com.android.bt.connected"
    private const val BT_DISCONNECTED = "com.android.bt.disconnected"
    private val AIOS = listOf(
        "action.bt.AIOS_INCOMING_RINGING", "action.bt.AIOS_INCOMING_OFFHOOK", "action.bt.AIOS_INCOMING_IDLE",
        "action.bt.AIOS_OUTGOING_RINGING", "action.bt.AIOS_OUTGOING_OFFHOOK", "action.bt.AIOS_OUTGOING_IDLE",
    )
}
