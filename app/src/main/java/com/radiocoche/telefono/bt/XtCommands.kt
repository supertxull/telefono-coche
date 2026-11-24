package com.radiocoche.telefono.bt

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * Servicio de sistema de la unidad (`com.tw.service.xt`, el que usan los asistentes de voz).
 * Reenvía las órdenes a la app de fábrica del Bluetooth (`com.tw.bt`), que es la única que habla con el módulo.
 *
 * Interfaz AIDL `com.tw.service.xt.aidl.ITWCommandAidl` (números de transacción sacados de com.tw.bt):
 *   btCall(String) = 19 · btAccept() = 20 · btHangUP() = 21 · btGetPhoneStatus() = 26 · btGetConnectedStatus() = 28
 *
 * Se habla con el Binder a mano (sin el archivo .aidl) para no depender de nada de la unidad.
 */
class XtCommands(private val ctx: Context) {

    private var binder: IBinder? = null
    private var bound = false
    private val pending = mutableListOf<(IBinder) -> Unit>()

    /** true si el servicio respondió. */
    val connected: Boolean get() = binder?.isBinderAlive == true

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service
            val list = pending.toList()
            pending.clear()
            service?.let { b -> list.forEach { runCatching { it(b) } } }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            bound = false
        }

        override fun onBindingDied(name: ComponentName?) {
            binder = null
            bound = false
            runCatching { ctx.unbindService(this) }
        }

        override fun onNullBinding(name: ComponentName?) {
            binder = null
            bound = false
            runCatching { ctx.unbindService(this) }
        }
    }

    fun bind() {
        if (bound) return
        val i = Intent(ACTION).setPackage(PKG)
        bound = runCatching { ctx.bindService(i, conn, Context.BIND_AUTO_CREATE) }
            .onFailure { Log.w(TAG, "No se pudo conectar con $PKG", it) }
            .getOrDefault(false)
    }

    fun unbind() {
        if (bound) runCatching { ctx.unbindService(conn) }
        bound = false
        binder = null
    }

    /** Llama al número. Devuelve false si el servicio no está (entonces se usa la app de fábrica). */
    fun call(number: String): Boolean = send(CODE_CALL) { it.writeString(number) }

    fun accept(): Boolean = send(CODE_ACCEPT)
    fun hangUp(): Boolean = send(CODE_HANGUP)

    private fun send(code: Int, args: (Parcel) -> Unit = {}): Boolean {
        val b = binder
        if (b == null || !b.isBinderAlive) {
            // Sin conexión no se deja nada en cola (se haría tarde): se intenta conectar para la próxima
            // y esta vez se usa la app de fábrica.
            bind()
            return false
        }
        return transact(b, code, args)
    }

    private fun transact(b: IBinder, code: Int, args: (Parcel) -> Unit): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            args(data)
            b.transact(code, data, reply, 0)
            reply.readException() // si el servicio rechaza la orden, cuenta como fallo
            true
        } catch (e: Exception) {
            Log.w(TAG, "Orden $code fallida", e)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    companion object {
        private const val TAG = "XtCommands"
        const val PKG = "com.tw.service.xt"
        const val ACTION = "com.tw.service.xt.CommandService.Bind"
        const val DESCRIPTOR = "com.tw.service.xt.aidl.ITWCommandAidl"
        const val CODE_CALL = 19
        const val CODE_ACCEPT = 20
        const val CODE_HANGUP = 21
    }
}
