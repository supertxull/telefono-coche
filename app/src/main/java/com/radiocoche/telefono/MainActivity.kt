package com.radiocoche.telefono

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.radiocoche.telefono.bt.PhoneLink
import com.radiocoche.telefono.data.Contact
import com.radiocoche.telefono.data.Contacts
import com.radiocoche.telefono.data.Favorites
import com.radiocoche.telefono.ui.CarLights
import com.radiocoche.telefono.ui.DayNight
import com.radiocoche.telefono.ui.PhoneScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { TECLADO, FAVORITOS, RECIENTES, CONTACTOS }

/** Estado de la pantalla principal. */
class PhoneUi(private val act: MainActivity) {
    var tab by mutableStateOf(Tab.TECLADO)
    var typed by mutableStateOf("")
    var contacts by mutableStateOf<List<Contact>>(emptyList())
        private set
    var favorites by mutableStateOf<Set<String>>(emptySet())
        private set
    var loadedAt by mutableStateOf(0L)
        private set
    var hasContactsPermission by mutableStateOf(false)
    var canOverlay by mutableStateOf(true)
    var group by mutableStateOf(0)
    var toast by mutableStateOf("")

    fun reload(scope: CoroutineScope) {
        favorites = Favorites.get(act)
        if (!hasContactsPermission) return
        scope.launch {
            contacts = withContext(Dispatchers.IO) { Contacts.load(act) }
            loadedAt = System.currentTimeMillis()
        }
    }

    fun toggleFav(c: Contact) {
        favorites = Favorites.toggle(act, c.key)
    }

    fun call(number: String, name: String) {
        PhoneLink.call(number, name.ifBlank { Contacts.pretty(number) })
        typed = ""
    }

    /** Nombre del contacto que tiene ese número (para Recientes y llamadas entrantes). */
    fun nameFor(number: String): String? {
        val k = Contacts.digits(number).takeLast(9)
        if (k.length < 6) return null
        return contacts.firstOrNull { it.key == k }?.name
    }

    fun reloadFromScreen() = act.reloadNow()
    fun askContacts() = act.askContacts()
    fun openOverlaySettings() = act.openOverlaySettings()
    fun openFactory() = PhoneLink.openFactory()
}

class MainActivity : ComponentActivity() {

    private val scope = MainScope()
    lateinit var ui: PhoneUi

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        ui.hasContactsPermission = ok
        ui.reload(scope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = PhoneUi(this)
        CarLights.start(this) { DayNight.apply() }
        DayNight.apply()
        PhoneService.start(this)
        PhoneLink.start(this)
        handleIntent(intent)
        setContent { PhoneScreen(ui) }
        ui.hasContactsPermission = hasContacts()
        if (!ui.hasContactsPermission) askContacts()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Desde otra app (tel:123…): se pone el número en el teclado. */
    private fun handleIntent(i: Intent?) {
        val data = i?.data ?: return
        if (data.scheme == "tel") {
            ui.tab = Tab.TECLADO
            ui.typed = Uri.decode(data.schemeSpecificPart).filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        }
    }

    override fun onResume() {
        super.onResume()
        visible = true
        DayNight.apply()
        ui.hasContactsPermission = hasContacts()
        ui.canOverlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)
        ui.reload(scope)
        PhoneLink.query()
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun hasContacts() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun askContacts() = askPermission.launch(Manifest.permission.READ_CONTACTS)

    fun reloadNow() {
        ui.reload(scope)
        ui.toast = "Agenda leída de nuevo"
    }

    fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23) return
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    companion object {
        /** La pantalla está a la vista (para abrir la llamada entrante aunque falte el permiso). */
        @Volatile var visible = false
    }
}
