# Geplanter Datei- und Modulzuschnitt

```text
nsn-neo/
├── app/
│   ├── src/main/       gemeinsame App-Bootstrap-Ressourcen
│   ├── src/mobile/     Smartphone-Manifest, UI und Ressourcen
│   └── src/tv/         Fire-TV-Manifest, UI und Ressourcen
├── core-model/         Content-, Episode-, Stream- und Sitzungsmodelle
├── core-source-api/    SourceProvider und Resolververträge
├── core-player/        PlaybackEngine und PlaybackCoordinator
├── core-session/       getrennte Cookie-/Sitzungsspeicher
├── core-database/      Favoriten, Verlauf und Fortschritt
├── source-aniworld/    AniWorld-Adapter
├── source-serienstreams/ SerienStreams-Adapter
├── source-filmpalast/  Filmpalast-Adapter und Serienfilter
├── feature-home/       Hero und Inhaltsreihen
├── feature-details/    native Detailseite
├── feature-search/     quellenübergreifende Suche
└── feature-account/    getrennte Kontostatus/-anmeldung
```

Die erste ausführbare Projektbasis beginnt bewusst kompakter. Module werden erst ausgelagert, sobald ihre Grenzen durch Tests abgesichert sind; dadurch bleibt jeder Zwischenschritt baubar.
