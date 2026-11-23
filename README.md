# Teléfono del coche

App de teléfono propia para la radio Witson (TopWay TS10) del Kia, con el mismo aspecto que Radio, Música y el launcher.

- El Bluetooth lo sigue llevando la app de fábrica `com.tw.bt`; esta app solo la usa por detrás.
- Llamar, contestar y colgar: servicio del sistema `com.tw.service.xt` (el de los asistentes de voz).
- Estado de la llamada: avisos de `com.tw.bt` (`SEND_CALL_INFO`, `action.bt.AIOS_*`).
- Agenda: la que copia la app de fábrica en la agenda de Android de la unidad (solo se lee).
- Favoritos con estrella y recientes, guardados en esta app.

La APK se compila sola con GitHub Actions en cada cambio: https://github.com/supertxull/telefono-coche/releases/latest/download/Telefono.apk
