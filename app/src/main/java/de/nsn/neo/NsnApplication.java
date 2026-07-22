package de.nsn.neo;

import android.app.Application;
import de.nsn.neo.player.PlaybackCoordinator;
import de.nsn.neo.data.LibraryStore;
import de.nsn.neo.source.SourceRegistry;
import de.nsn.neo.source.aniworld.AniWorldSource;
import de.nsn.neo.source.filmpalast.FilmpalastSource;
import de.nsn.neo.source.serienstreams.SerienStreamsSource;

public final class NsnApplication extends Application {
    private final SourceRegistry sourceRegistry = new SourceRegistry();
    private PlaybackCoordinator playbackCoordinator;
    private LibraryStore libraryStore;
    @Override public void onCreate() {
        super.onCreate();
        sourceRegistry.register(new AniWorldSource());
        sourceRegistry.register(new SerienStreamsSource());
        sourceRegistry.register(new FilmpalastSource());
    }
    public SourceRegistry sources() { return sourceRegistry; }
    public synchronized PlaybackCoordinator playback() {
        if (playbackCoordinator == null) playbackCoordinator = new PlaybackCoordinator(this);
        return playbackCoordinator;
    }
    public synchronized LibraryStore library() {
        if (libraryStore == null) libraryStore = new LibraryStore(this);
        return libraryStore;
    }
}
