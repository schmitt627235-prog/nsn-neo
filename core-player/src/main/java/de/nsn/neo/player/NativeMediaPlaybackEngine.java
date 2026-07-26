package de.nsn.neo.player;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import de.nsn.neo.model.ResolvedStream;
import java.util.HashMap;

/** Main playback engine proven compatible with the Fire-TV hoster streams. */
public final class NativeMediaPlaybackEngine implements PlaybackEngine {
    private final Context context;
    private TextureView surface;
    private Surface outputSurface;
    private MediaPlayer player;
    private ViewGroup parent;
    private ResolvedStream pending;
    private long pendingPosition;
    private boolean pendingPlay;
    private String currentUrl;
    private Runnable onEnded;

    public NativeMediaPlaybackEngine(Context context){this.context=context;}
    @Override public synchronized void attach(ViewGroup parent,boolean controlsVisible){
        this.parent=parent;if(surface==null){surface=new TextureView(parent.getContext());surface.setOpaque(true);surface.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height){synchronized(NativeMediaPlaybackEngine.this){releaseOutputSurface();outputSurface=new Surface(texture);if(pending!=null)startPending(outputSurface);else if(player!=null)player.setSurface(outputSurface);}}
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){synchronized(NativeMediaPlaybackEngine.this){if(player!=null)player.setSurface(null);releaseOutputSurface();}return true;}
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){}
        });}if(surface.getParent() instanceof ViewGroup)((ViewGroup)surface.getParent()).removeView(surface);parent.addView(surface,0,new ViewGroup.LayoutParams(-1,-1));if(surface.isAvailable()){releaseOutputSurface();outputSurface=new Surface(surface.getSurfaceTexture());if(pending!=null)startPending(outputSurface);else if(player!=null)player.setSurface(outputSurface);}
    }
    @Override public synchronized void prepare(ResolvedStream stream,long positionMs,boolean playWhenReady){pending=stream;pendingPosition=Math.max(0,positionMs);pendingPlay=playWhenReady;currentUrl=stream.url;if(outputSurface!=null&&outputSurface.isValid())startPending(outputSurface);}
    private void startPending(Surface target){
        releasePlayer();ResolvedStream stream=pending;pending=null;
        try{android.util.Log.i("NSN_PLAYER","setDataSource "+stream.url);player=new MediaPlayer();player.setSurface(target);player.setScreenOnWhilePlaying(true);player.setOnPreparedListener(mp->{android.util.Log.i("NSN_PLAYER","prepared duration="+mp.getDuration()+" video="+mp.getVideoWidth()+"x"+mp.getVideoHeight());if(pendingPosition>0)mp.seekTo((int)Math.min(Integer.MAX_VALUE,pendingPosition));if(pendingPlay)mp.start();});player.setOnVideoSizeChangedListener((mp,w,h)->android.util.Log.i("NSN_PLAYER","video size "+w+"x"+h));player.setOnInfoListener((mp,what,extra)->{android.util.Log.i("NSN_PLAYER","info "+what+"/"+extra);return false;});player.setOnErrorListener((mp,what,extra)->{android.util.Log.e("NSN_PLAYER","error "+what+"/"+extra);return true;});player.setOnCompletionListener(mp->{if(onEnded!=null)onEnded.run();});player.setDataSource(context,Uri.parse(stream.url),stream.headers==null?new HashMap<>():new HashMap<>(stream.headers));player.prepareAsync();}catch(Exception error){android.util.Log.e("NSN_PLAYER","prepare failed",error);releasePlayer();}
    }
    @Override public synchronized void detach(){if(surface!=null&&surface.getParent() instanceof ViewGroup)((ViewGroup)surface.getParent()).removeView(surface);parent=null;}
    @Override public synchronized void pause(){if(player!=null&&player.isPlaying())player.pause();}
    @Override public synchronized void play(){if(player!=null)player.start();}
    @Override public synchronized void stop(){releasePlayer();pending=null;currentUrl=null;}
    @Override public synchronized long positionMs(){try{return player==null?0:player.getCurrentPosition();}catch(IllegalStateException ignored){return 0;}}
    @Override public synchronized long durationMs(){try{return player==null?0:Math.max(0,player.getDuration());}catch(IllegalStateException ignored){return 0;}}
    @Override public synchronized void setOnEndedListener(Runnable listener){onEnded=listener;}
    @Override public synchronized void seekTo(long positionMs){if(player!=null)player.seekTo((int)Math.min(Integer.MAX_VALUE,Math.max(0,positionMs)));}
    @Override public synchronized void setVolume(float volume){if(player!=null)player.setVolume(volume,volume);}
    @Override public synchronized void setRepeat(boolean repeat){if(player!=null)player.setLooping(repeat);}
    @Override public synchronized boolean isPlaying(){return player!=null&&player.isPlaying();}
    @Override public synchronized String currentUrl(){return currentUrl;}
    @Override public synchronized void release(){detach();releasePlayer();releaseOutputSurface();surface=null;pending=null;currentUrl=null;}
    private void releasePlayer(){if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}}
    private void releaseOutputSurface(){if(outputSurface!=null){outputSurface.release();outputSurface=null;}}
}
