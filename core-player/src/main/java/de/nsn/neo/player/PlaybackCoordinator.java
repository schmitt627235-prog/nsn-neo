package de.nsn.neo.player;

import android.content.Context;

/** Owns exactly one main player and at most one mutually-exclusive muted trailer player. */
public final class PlaybackCoordinator {
    private final PlaybackEngine main;
    private PlaybackEngine trailer;

    public PlaybackCoordinator(Context context) { main = new NativeMediaPlaybackEngine(context); }
    public synchronized PlaybackEngine main() { stopTrailer(); return main; }
    public synchronized PlaybackEngine trailer(Context context) {
        if (main.isPlaying()) main.pause();
        if (trailer == null) trailer = new Media3PlaybackEngine(context);
        return trailer;
    }
    public synchronized void stopTrailer() { if (trailer != null) { trailer.release(); trailer = null; } }
    public synchronized void releaseAll() { stopTrailer(); main.release(); }
}
