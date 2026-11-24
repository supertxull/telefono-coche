package com.radiocoche.telefono.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radiocoche.telefono.PhoneUi
import com.radiocoche.telefono.Tab
import com.radiocoche.telefono.bt.CallState
import com.radiocoche.telefono.bt.PhoneLink
import com.radiocoche.telefono.bt.Recents
import com.radiocoche.telefono.data.Contact
import com.radiocoche.telefono.data.Contacts
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun PhoneScreen(ui: PhoneUi) {
    val p = Tema.p
    Box(Modifier.fillMaxSize().background(p.bg)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NavColumn(ui)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Header(ui)
                when (ui.tab) {
                    Tab.TECLADO -> Keypad(ui)
                    Tab.FAVORITOS -> Favs(ui)
                    Tab.RECIENTES -> RecentsList(ui)
                    Tab.CONTACTOS -> ContactsList(ui)
                }
            }
        }
        // Sin permiso para ponerse encima de otras apps, la llamada se ve aquí dentro.
        if (!ui.canOverlay || !com.radiocoche.telefono.CallOverlay.showingState) CallLayers()
        if (ui.toast.isNotEmpty()) {
            LaunchedEffect(ui.toast) {
                delay(2200)
                ui.toast = ""
            }
            Toast(ui.toast)
        }
    }
}

// ------------------------------------------------------------------ izquierda

@Composable
private fun NavColumn(ui: PhoneUi) {
    val p = Tema.p
    Column(Modifier.width(196.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.height(48.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val name = PhoneLink.phoneName
            CarIcon(Ic.Bluetooth, 26.dp, if (name.isNotEmpty()) p.accent else p.dim, "Bluetooth")
            Text(
                name.ifEmpty { "Sin móvil" },
                color = if (name.isNotEmpty()) p.accent else p.dim, fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        NavButton(ui, Tab.TECLADO, Ic.Keypad, "Teclado")
        NavButton(ui, Tab.FAVORITOS, Ic.Star, "Favoritos")
        NavButton(ui, Tab.RECIENTES, Ic.Clock, "Recientes")
        NavButton(ui, Tab.CONTACTOS, Ic.Contact, "Contactos")
    }
}

@Composable
private fun ColumnScope.NavButton(ui: PhoneUi, tab: Tab, icon: ImageVector, label: String) {
    val p = Tema.p
    val on = ui.tab == tab
    val style = if (on) BtnStyle(p.accentBg, p.accent, p.text, borderWidth = 2.dp) else BtnStyle(p.panel, p.border, p.muted)
    CarButton(Modifier.weight(1f).fillMaxWidth(), style, radius = 18.dp, onClick = { ui.tab = tab }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CarIcon(icon, 32.dp, if (on) p.accent else p.muted)
            Text(label, color = style.fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ------------------------------------------------------------------ cabecera

@Composable
private fun Header(ui: PhoneUi) {
    val p = Tema.p
    Row(
        Modifier.height(48.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
    ) {
        if (!ui.canOverlay) {
            SmallButton("Permitir llamadas encima de otras apps", accent = true) { ui.openOverlaySettings() }
        }
        if (ui.tab == Tab.CONTACTOS && ui.hasContactsPermission) {
            val t = if (ui.loadedAt > 0) SimpleDateFormat("HH:mm", Locale("es")).format(Date(ui.loadedAt)) else ""
            Text(
                "${ui.contacts.size} números" + if (t.isNotEmpty()) " · leídos a las $t" else "",
                color = p.muted, fontSize = 16.sp,
            )
            SmallButton("Actualizar", icon = Ic.Sync) { ui.reloadFromScreen() }
        }
    }
}

@Composable
private fun SmallButton(text: String, icon: ImageVector? = null, accent: Boolean = false, onClick: () -> Unit) {
    val p = Tema.p
    val style = if (accent) BtnStyle(p.panel, p.accent, p.accent, borderWidth = 2.dp) else BtnStyle(p.raised, p.border2, p.text)
    CarButton(Modifier.height(48.dp), style, radius = 14.dp, onClick = onClick) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) CarIcon(icon, 22.dp, style.fg)
            Text(text, color = style.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ teclado

@Composable
private fun ColumnScope.Keypad(ui: PhoneUi) {
    val p = Tema.p
    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(392.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(16.dp)).background(p.well)
                    .border(1.dp, p.border, RoundedCornerShape(16.dp)).padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ui.typed.isEmpty()) {
                    Text("Marca un número", color = p.dim, fontSize = 22.sp, modifier = Modifier.weight(1f))
                } else {
                    Text(
                        Contacts.pretty(ui.typed), color = p.text, fontSize = 44.sp, fontFamily = Tema.condensed,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                }
                CarButton(
                    Modifier.size(60.dp), BtnStyle(Color.Transparent, null, p.muted), radius = 14.dp,
                    onHold = { ui.typed = "" }, onClick = { ui.typed = ui.typed.dropLast(1) },
                ) { CarIcon(Ic.Backspace, 32.dp, p.muted, "Borrar último número") }
            }
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in keys.chunked(3)) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (k in row) {
                            CarButton(
                                Modifier.weight(1f).fillMaxHeight(), BtnStyle(p.raised, p.border2, p.text),
                                // Mantener el 0 pulsado pone el +.
                                onHold = if (k == "0") ({ if (ui.typed.length < 16 && !ui.typed.endsWith("+")) ui.typed += "+" }) else null,
                                onClick = { if (ui.typed.length < 16) ui.typed += k },
                            ) {
                                Text(k, color = p.text, fontSize = 48.sp, fontFamily = Tema.condensed)
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val digits = Contacts.digits(ui.typed)
            val matches = if (digits.length >= 2) ui.contacts.filter { Contacts.digits(it.number).contains(digits) }.take(4) else emptyList()
            Column(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.panel)
                    .border(1.dp, p.border, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("COINCIDENCIAS", color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                if (matches.isEmpty()) {
                    Text(
                        if (digits.length >= 2) "Ningún contacto tiene este número." else "Al marcar aparecen aquí los contactos con ese número.",
                        color = p.muted, fontSize = 18.sp,
                    )
                } else {
                    for (m in matches) {
                        CarButton(Modifier.fillMaxWidth().height(64.dp), BtnStyle(p.raised, p.border, p.text), radius = 14.dp, onClick = { ui.call(m.number, m.name) }) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(m.initials, 42.dp, 18.sp)
                                Column {
                                    Text(m.name, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(Contacts.pretty(m.number), color = p.muted, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
            val can = digits.length >= 3
            CarButton(
                Modifier.fillMaxWidth().height(88.dp),
                if (can) BtnStyle(p.call, null, p.onCall) else BtnStyle(p.raised, null, p.dim),
                radius = 20.dp, enabled = can,
                onClick = {
                    val m = ui.contacts.firstOrNull { Contacts.digits(it.number) == digits }
                    ui.call(ui.typed, m?.name ?: "")
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CarIcon(Ic.Phone, 34.dp, if (can) p.onCall else p.dim)
                    Text("Llamar", color = if (can) p.onCall else p.dim, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ favoritos

@Composable
private fun ColumnScope.Favs(ui: PhoneUi) {
    val p = Tema.p
    val favs = ui.contacts.filter { it.key in ui.favorites }
    if (favs.isEmpty()) {
        Empty(
            if (ui.hasContactsPermission) "Aún no hay favoritos" else "Sin permiso para leer la agenda",
            if (ui.hasContactsPermission) "Toca la estrella de un contacto en Contactos para tenerlo aquí." else "Pulsa aquí para darlo.",
        ) { if (!ui.hasContactsPermission) ui.askContacts() }
        return
    }
    LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.weight(1f).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(favs, key = { it.name + it.number }) { f ->
            CarButton(Modifier.fillMaxWidth().height(158.dp), BtnStyle(p.panel, p.border2, p.text), radius = 18.dp, onClick = { ui.call(f.number, f.name) }) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatar(f.initials, 64.dp, 26.sp)
                    Text(f.name, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Contacts.pretty(f.number), color = p.muted, fontSize = 16.sp, maxLines = 1)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ recientes

@Composable
private fun ColumnScope.RecentsList(ui: PhoneUi) {
    val p = Tema.p
    val list = Recents.list
    if (list.isEmpty()) {
        Empty("Aún no hay llamadas", "Aquí se apuntan las llamadas que hagas y recibas a partir de ahora.") {}
        return
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list) { r ->
            val shown = r.name.takeIf { it.isNotBlank() && it != r.number } ?: ui.nameFor(r.number) ?: Contacts.pretty(r.number)
            Row(
                Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(16.dp)).background(p.panel)
                    .border(1.dp, p.border, RoundedCornerShape(16.dp)).padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (r.kind) {
                    Recents.Kind.IN -> CarIcon(Ic.CallIn, 28.dp, p.muted, "Recibida")
                    Recents.Kind.OUT -> CarIcon(Ic.CallOut, 28.dp, p.accent, "Realizada")
                    Recents.Kind.MISSED -> CarIcon(Ic.CallMissed, 28.dp, p.missed, "Perdida")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        shown, color = if (r.kind == Recents.Kind.MISSED) p.missed else p.text,
                        fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val detail = when (r.kind) {
                        Recents.Kind.IN -> "Recibida"
                        Recents.Kind.OUT -> "Realizada"
                        Recents.Kind.MISSED -> "Perdida"
                    } + if (r.seconds > 0) " · " + dur(r.seconds) else ""
                    Text(detail, color = p.muted, fontSize = 16.sp)
                }
                Text(whenText(r.time), color = p.muted, fontSize = 18.sp)
                CallSmall { ui.call(r.number, shown) }
            }
        }
    }
}

private fun dur(s: Int) = "%d:%02d".format(s / 60, s % 60)

private fun whenText(t: Long): String {
    val c = Calendar.getInstance()
    val today = c.get(Calendar.DAY_OF_YEAR) to c.get(Calendar.YEAR)
    c.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = c.get(Calendar.DAY_OF_YEAR) to c.get(Calendar.YEAR)
    c.timeInMillis = t
    val day = c.get(Calendar.DAY_OF_YEAR) to c.get(Calendar.YEAR)
    val hm = SimpleDateFormat("HH:mm", Locale("es")).format(Date(t))
    return when (day) {
        today -> hm
        yesterday -> "Ayer $hm"
        else -> SimpleDateFormat("d MMM", Locale("es")).format(Date(t))
    }
}

// ------------------------------------------------------------------ contactos

private val GROUPS = listOf("Todos" to "", "ABC" to "abc", "DEF" to "def", "GHI" to "ghi", "JKL" to "jkl", "MNÑO" to "mnño", "PQR" to "pqr", "STU" to "stu", "VWXYZ" to "vwxyz")

@Composable
private fun ColumnScope.ContactsList(ui: PhoneUi) {
    val p = Tema.p
    if (!ui.hasContactsPermission) {
        Empty("Sin permiso para leer la agenda", "Pulsa aquí para darlo.") { ui.askContacts() }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GROUPS.forEachIndexed { i, (label, _) ->
            val on = ui.group == i
            CarButton(
                Modifier.weight(1f).height(52.dp),
                if (on) BtnStyle(p.accentBg, p.accent, p.accent, borderWidth = 2.dp) else BtnStyle(p.panel, p.border, p.muted),
                radius = 14.dp, onClick = { ui.group = i },
            ) { Text(label, color = if (on) p.accent else p.muted, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
    val letters = GROUPS[ui.group].second
    val list = if (letters.isEmpty()) ui.contacts else ui.contacts.filter {
        val first = Contacts.fold(it.name).firstOrNull() ?: ' '
        // La ñ se queda como ñ al quitar acentos (la tilde de la ñ no es un acento combinable aparte en NFD).
        val f = if (it.name.trim().lowercase().startsWith("ñ")) 'ñ' else first
        letters.contains(f)
    }
    if (ui.contacts.isEmpty()) {
        Empty(
            "La agenda está vacía",
            "Los contactos los copia la app del Bluetooth al conectar el móvil. Si acabas de conectarlo, pulsa Actualizar en un momento.",
        ) {}
        return
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.name + "|" + it.number }) { c ->
            ContactRow(ui, c)
        }
    }
}

@Composable
private fun ContactRow(ui: PhoneUi, c: Contact) {
    val p = Tema.p
    val fav = c.key in ui.favorites
    Row(
        Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(16.dp)).background(p.panel)
            .border(1.dp, p.border, RoundedCornerShape(16.dp)).padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(c.initials, 46.dp, 19.sp)
        Column(Modifier.weight(1f)) {
            Text(c.name, color = p.text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                Contacts.pretty(c.number) + if (c.label.isNotEmpty()) " · " + c.label else "",
                color = p.muted, fontSize = 16.sp, maxLines = 1,
            )
        }
        CarButton(Modifier.size(56.dp), BtnStyle(Color.Transparent, null, p.accent), radius = 14.dp, onClick = { ui.toggleFav(c) }) {
            CarIcon(if (fav) Ic.StarOn else Ic.Star, 32.dp, if (fav) p.accent else p.dim, if (fav) "Quitar de favoritos" else "Añadir a favoritos")
        }
        CallSmall { ui.call(c.number, c.name) }
    }
}

// ------------------------------------------------------------------ piezas

@Composable
private fun CallSmall(onClick: () -> Unit) {
    val p = Tema.p
    CarButton(Modifier.width(60.dp).height(56.dp), BtnStyle(p.call, null, p.onCall), radius = 14.dp, onClick = onClick) {
        CarIcon(Ic.Phone, 28.dp, p.onCall, "Llamar")
    }
}

@Composable
private fun Avatar(initials: String, size: Dp, font: TextUnit) {
    val p = Tema.p
    Box(Modifier.size(size).clip(CircleShape).background(p.accentBg), contentAlignment = Alignment.Center) {
        Text(initials, color = p.accent, fontSize = font, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ColumnScope.Empty(title: String, text: String, onClick: () -> Unit) {
    val p = Tema.p
    CarButton(Modifier.weight(1f).fillMaxWidth(), BtnStyle(p.panel, p.border, p.text), radius = 20.dp, onClick = onClick) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(text, color = p.muted, fontSize = 19.sp, textAlign = TextAlign.Center)
        }
    }
}

// ------------------------------------------------------------------ llamadas

/** Pantalla de llamada (entrante o en curso) según el estado; vacía si no hay llamada. */
@Composable
fun BoxScope.CallLayers() {
    when (PhoneLink.state) {
        CallState.INCOMING -> Incoming()
        CallState.ACTIVE, CallState.DIALING -> InCall()
        CallState.IDLE -> {}
    }
}

@Composable
private fun BoxScope.CallLayer(content: @Composable ColumnScope.() -> Unit) {
    val p = Tema.p
    Column(
        Modifier.matchParentSize().background(p.bg).padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun ColumnScope.Who() {
    val p = Tema.p
    val number = PhoneLink.number
    val name = PhoneLink.name.takeIf { it.isNotBlank() && it != number } ?: com.radiocoche.telefono.data.ContactCache.nameFor(number) ?: ""
    val big = name.ifEmpty { Contacts.pretty(number).ifEmpty { "Número oculto" } }
    Column(
        Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Avatar(Contacts.initials(name.ifEmpty { "#" }), 120.dp, 46.sp)
        Text(big, color = p.text, fontSize = 60.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (name.isNotEmpty() && number.isNotEmpty()) Text(Contacts.pretty(number), color = p.muted, fontSize = 26.sp)
    }
}

@Composable
private fun BoxScope.InCall() {
    val p = Tema.p
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    CallLayer {
        val since = PhoneLink.activeSince
        val status = if (PhoneLink.state == CallState.ACTIVE && since > 0) "EN LLAMADA · " + dur(((now - since) / 1000).toInt()) else "LLAMANDO…"
        Text(status, color = p.accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Who()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Silenciar el micrófono, el teclado durante la llamada y pasar al móvil solo los hace la app de fábrica.
            CarButton(Modifier.weight(1f).height(96.dp), BtnStyle(p.raised, p.border2, p.text), radius = 18.dp, onClick = {
                com.radiocoche.telefono.CallOverlay.hideByUser()
                PhoneLink.openFactory()
            }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CarIcon(Ic.Keypad, 32.dp, p.text)
                    Text("Más opciones", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            CarButton(
                Modifier.weight(1.4f).height(96.dp), BtnStyle(p.hang, null, Color.White), radius = 18.dp,
                onHold = { PhoneLink.forceIdle() }, onClick = { PhoneLink.hangUp() },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CarIcon(Ic.HangUp, 36.dp, Color.White)
                    Text("Colgar", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Incoming() {
    val p = Tema.p
    CallLayer {
        Text("LLAMADA ENTRANTE", color = p.accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Who()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CarButton(
                Modifier.weight(1f).height(112.dp), BtnStyle(p.hang, null, Color.White), radius = 20.dp,
                onHold = { PhoneLink.forceIdle() }, onClick = { PhoneLink.hangUp() },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CarIcon(Ic.HangUp, 40.dp, Color.White)
                    Text("Rechazar", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
            CarButton(Modifier.weight(1f).height(112.dp), BtnStyle(p.call, null, p.onCall), radius = 20.dp, onClick = { PhoneLink.accept() }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CarIcon(Ic.Phone, 40.dp, p.onCall)
                    Text("Contestar", color = p.onCall, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
