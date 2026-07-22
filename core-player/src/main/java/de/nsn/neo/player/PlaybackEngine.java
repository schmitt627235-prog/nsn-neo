package de.nsn.neo.player;

import android.view.ViewGroup;
import de.nsn.neo.model.ResolvedStream;

public interface PlaybackEngine {
    void attach(ViewGroup parent, boolean controlsVisible);
    void detach();
    void prepare(ResolvedStream stream, long positionMs, boolean playWhenReady);
    void pause();
    void play();
    void stop();
    long positionMs();
    long durationMs();
    void setOnEndedListener(Runnable listener);
    void seekTo(long positionMs);
    void setVolume(float volume);
    void setRepeat(boolean repeat);
    boolean isPlaying();
    String currentUrl();
    void release();
}
