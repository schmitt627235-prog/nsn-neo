# AniWorld Shield

Android-WebView-App für `http://186.2.175.111/` mit Navigation, Login-/Cookie-Unterstützung,
HTML5-Vollbildvideo, Pop-up-Sperre und lokalem Werbe-/Trackingfilter.

## Build

1. Projektordner in Android Studio öffnen.
2. Mit installiertem Android SDK 35 synchronisieren.
3. `Build > Build APK(s)` wählen oder `gradlew assembleDebug` ausführen.

Die App benötigt Android 8.0 (API 26) oder neuer. Der Schild in der unteren Leiste
schaltet den Filter ein und aus. Drittanbieter-Cookies, Datei- und Content-Zugriffe
sind aus Sicherheitsgründen deaktiviert.

Hinweis: Brave Shields ist Bestandteil von Brave/Chromium und keine separat einbettbare
Android-WebView-Bibliothek. Diese App verwendet daher einen eigenen, Brave-orientierten
Request-, Pop-up- und Cosmetic-Filter.


## Version 1.0.3 – nativer Fire-TV-Player

Direkte HLS/MP4-Anfragen werden in der WebView abgefangen und dort blockiert.
Die WebView dient nur noch zum Auflösen der Hoster-Seite; die Wiedergabe erfolgt
ausschließlich über den nativen Android MediaPlayer. Vor dem Start werden alle
HTML5-Medien pausiert und die WebView ausgeblendet, wodurch doppelter Ton und
der schwarze Overlay-Fehler vermieden werden. Cookies, User-Agent und Referer
werden an den nativen Player übergeben.
