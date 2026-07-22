# Priorisierter Migrationsplan

## Phase 1 – Sicherung und Projektbasis

- Referenz-APKs bauen und Prüfsummen erfassen.
- Neues gemeinsames NSN-Projekt anlegen.
- eindeutige Varianten `mobile` und `tv` mit `de.nsn.neo.mobile` und `de.nsn.neo.tv`.
- gemeinsame Modelle, Provider-Vertrag und Player-Verträge erstellen.
- beide Varianten ohne Funktionsablösung bauen.

## Phase 2 – Quellenadapter

- AniWorld-, SerienStreams- und Filmpalast-Provider getrennt anbinden.
- bestehende Resolver-/Cookie-Logik zunächst unverändert hinter Adaptern verwenden.
- Filmpalast-Serienfilter mit Tests ergänzen.
- identische laufende Requests deduplizieren.

## Phase 3 – Playerkern

- bisherige Wiedergabe hinter `PlaybackEngine` kapseln.
- Media3-Implementierung für HLS, DASH und MP4 ergänzen.
- Header, Cookie, Referer, User-Agent und Untertitel vergleichen.
- Rotation und Lifecycle ohne zweite Instanz testen.

## Phase 4 – Native TV-Oberfläche

- Hero, horizontale Rails, Suche und Konten.
- Detailseite, Staffel/Episode/Sprache/Hoster.
- D-Pad-Fokus, Skalierung und Fokuswiederherstellung.
- automatisch ausblendbare obere Navigation.

## Phase 5 – Native Smartphone-Oberfläche

- Touch-/Wischsteuerung und Pull-to-Refresh.
- adaptive Hoch-/Querformat-Layouts.
- Zustand über Rotation erhalten.
- Navigation abhängig von Scrollrichtung ein-/ausblenden.

## Phase 6 – Trailer und Bibliothek

- separater stummer Trailerplayer mit verzögertem Start und Backdrop-Fallback.
- Favoriten, Verlauf, Weiterschauen und nächste Episode.
- Wiedergabeposition pro stabiler Inhalts-/Episoden-ID.

## Phase 7 – Konten und Härtung

- getrennte AniWorld- und SerienStreams-Sitzungen.
- Filmpalast ohne Login.
- Performance-, Parser-, Lifecycle-, Fokus- und Player-Tests.
- signierte Mobile- und TV-APKs.

