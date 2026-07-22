# NEXT-STREAMING-NEO – Bestandsaufnahme

Stand: 18.07.2026

## Vorhandene Referenzprojekte

| Projekt | Zweck | Zustand |
|---|---|---|
| `work/aniworld-neo` | Smartphone-/WebView-Prototyp für AniWorld | baut reproduzierbar; WebView-UI und JS-Injektion |
| `work/serienstreams-neo-tv` | Fire-TV-Prototyp für SerienStreams | baut reproduzierbar; native `MediaPlayer`-Wiedergabe wurde auf Fire TV mit Bild/Ton getestet |
| `work/aniworld-shield-tv` | ältere Fire-TV-WebView-Basis | Referenz für Navigation und Filter, nicht Zielarchitektur |

Die unveränderten Ausgangsartefakte liegen als `outputs/baseline-*.apk` vor. Sie dienen als Rückfall- und Vergleichsstand.

## Bestehende Architektur

Die Projekte bestehen jeweils aus einem Android-App-Modul. Netzwerkzugriff, WebView, Ad-Blocking, Navigation, JS-Injektion, Streamauflösung und Playersteuerung befinden sich überwiegend in einer einzigen `MainActivity`.

- AniWorld: etwa 291 Zeilen in `MainActivity`, eine sichtbare WebView, CSS-/JavaScript-Injektion und WebView-Vollbildwiedergabe.
- SerienStreams: etwa 494 Zeilen in `MainActivity`, Haupt-WebView, temporäre Player-WebView, `SurfaceView`, nativer `MediaPlayer`, Cookie-/Referer-/User-Agent-Weitergabe und TV-Cursorsteuerung.
- Beide Apps verwenden einen hostlistenbasierten `AdBlocker` und kosmetische DOM-Filter.
- Katalog- und Detaildarstellung wird durch `neo_home.js` und `neo_detail.js` nachträglich in die Quellseite injiziert.

## Nachweislich funktionierende Komponenten

1. Laden der Quellseiten einschließlich JavaScript.
2. Persistente WebView-Cookies für den jeweiligen Host.
3. Login über die Originalformulare der Quellen.
4. Erkennung von HLS-/MP4-Anfragen im SerienStreams-Prototyp.
5. Übergabe von User-Agent, Cookie und Referer an den nativen Player.
6. Fire-TV-Wiedergabe mit Bild und Ton im zuletzt bestätigten Referenzstand.
7. Grundlegende D-Pad-Steuerung und Bildschirmtastatur.
8. Werbe-/Popup-Unterdrückung über Requestfilter und DOM-Bereinigung (nicht vollständig für alle Hoster).
9. Neo-Branding und Ladebild.

## Technische Schulden und Fehlerquellen

1. Gradle-`applicationId`, Manifest-Paket und Java-Paket widersprechen sich teilweise.
2. AniWorld und SerienStreams verwenden in Gradle dieselbe Application-ID.
3. Versionsnummern in Manifest und Gradle sind nicht konsistent.
4. Monolithische Activities koppeln UI, Quelle, Sitzung, Parser und Wiedergabe.
5. Bis zu zwei WebViews plus nativer Player können gleichzeitig existieren.
6. Listener und Lifecycle-Besitz sind nicht zentral geregelt.
7. Streamauflösung kann bei Navigation oder Rotation mehrfach ausgeführt werden.
8. WebView-Cookies sind global statt explizit pro Quelle isoliert.
9. JavaScript-Parser hängen stark von veränderlichem HTML und einzelnen CSS-Selektoren ab.
10. `neo_detail.js` kann bei fehlendem/lazy geladenem Cover vorzeitig abbrechen.
11. Smartphone und TV teilen keine echte Domänenlogik.
12. Favoriten, Verlauf und Wiedergabefortschritt besitzen kein stabiles gemeinsames Datenmodell.
13. Der vorhandene manuelle Build meldet unter JDK 21 beim Schließen von `android.jar` gelegentlich einen Windows-`AccessDeniedException`, erzeugt aber verifizierte APKs. Der neue Gradle-Build muss dieses Tooling-Risiko beseitigen.

## Verbindliche Migrationsregeln

- Parser, Login, Cookie-Weitergabe, Hosterlogik und funktionierende Streamauflösung werden zunächst nur über Adapter gekapselt.
- Keine quellspezifische HTML-Logik in UI-Modulen.
- Genau eine aktive Hauptplayerinstanz.
- Eine Resolver-WebView ist unsichtbar, stumm, kurzlebig und wird nach der Auflösung freigegeben.
- Trailer und Hauptplayer werden von einem zentralen Koordinator gegenseitig ausgeschlossen.
- Jede Phase muss Mobile und TV bauen lassen.
- Vor jeder funktionalen Ablösung bleibt ein Rückfallpfad auf den bestätigten Referenzstand erhalten.

