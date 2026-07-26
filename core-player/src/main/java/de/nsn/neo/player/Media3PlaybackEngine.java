package de.nsn.neo.player;

import android.content.Context;
import android.net.Uri;
import android.view.ViewGroup;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import java.util.HashMap;
import java.util.Map;

public final class Media3PlaybackEngine implements PlaybackEngine {
    private final Context context;
    private ExoPlayer player;
    private PlayerView view;
    private String currentUrl;
    private Runnable onEnded;

    public Media3PlaybackEngine(Context context) { this.context = context.getApplicationContext(); }

    @Override public synchronized void attach(ViewGroup parent, boolean controlsVisible) {
        ensurePlayer(new HashMap<>());
        if (view == null) {
            view = new PlayerView(parent.getContext());
            view.setBackgroundColor(android.graphics.Color.BLACK);
            view.setShutterBackgroundColor(android.graphics.Color.BLACK);
            view.setKeepContentOnPlayerReset(false);
        }
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        view.setUseController(controlsVisible); view.setPlayer(player);
        parent.addView(view, new ViewGroup.LayoutParams(-1, -1));
    }

    @Override public synchronized void detach() {
        if (view != null && view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
    }

    @Override public synchronized void prepare(de.nsn.neo.model.ResolvedStream stream, long positionMs, boolean playWhenReady) {
        boolean same = stream.url != null && stream.url.equals(currentUrl) && player != null;
        if (!same) {
            releasePlayerOnly(); ensurePlayer(stream.headers);
            MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(stream.url));
            if (stream.mimeType != null && !stream.mimeType.isEmpty()) item.setMimeType(stream.mimeType);
            player.setMediaItem(item.build()); player.prepare(); currentUrl = stream.url;
        }
        if (positionMs > 0 && (!same || Math.abs(player.getCurrentPosition() - positionMs) > 1500)) player.seekTo(positionMs);
        player.setPlayWhenReady(playWhenReady);
    }

    private void ensurePlayer(Map<String,String> headers) {
        if (player != null) return;
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true);
        if (headers != null && !headers.isEmpty()) http.setDefaultRequestProperties(headers);
        player = new ExoPlayer.Builder(context).setMediaSourceFactory(new DefaultMediaSourceFactory(http)).build();
        player.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&onEnded!=null)onEnded.run();}});
        if (view != null) view.setPlayer(player);
    }

    @Override public synchronized void pause() { if (player != null) player.pause(); }
    @Override public synchronized void play() { if (player != null) player.play(); }
    @Override public synchronized void stop() { if (player != null) player.stop(); currentUrl = null; }
    @Override public synchronized long positionMs() { return player == null ? 0 : player.getCurrentPosition(); }
    @Override public synchronized long durationMs() { return player == null ? 0 : Math.max(0, player.getDuration()); }
    @Override public synchronized void setOnEndedListener(Runnable listener){onEnded=listener;}
    @Override public synchronized void seekTo(long positionMs){if(player!=null)player.seekTo(Math.max(0,positionMs));}
    @Override public synchronized void setVolume(float volume){ensurePlayer(new HashMap<>());player.setVolume(Math.max(0f,Math.min(1f,volume)));}
    @Override public synchronized void setRepeat(boolean repeat){ensurePlayer(new HashMap<>());player.setRepeatMode(repeat?Player.REPEAT_MODE_ONE:Player.REPEAT_MODE_OFF);}
    @Override public synchronized boolean isPlaying() { return player != null && player.isPlaying(); }
    @Override public synchronized String currentUrl() { return currentUrl; }
    @Override public synchronized void release() { detach(); releasePlayerOnly(); view = null; }

    private void releasePlayerOnly() {
        if (player != null) { if (view != null) view.setPlayer(null); player.release(); player = null; }
        currentUrl = null;
    }
}
