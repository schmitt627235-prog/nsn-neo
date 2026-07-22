package de.nsn.neo.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import androidx.media3.common.MimeTypes;
import de.nsn.neo.NsnApplication;
import de.nsn.neo.model.ResolvedStream;
import de.nsn.neo.model.SourceId;
import de.nsn.neo.model.Episode;
import de.nsn.neo.model.HosterOption;
import de.nsn.neo.data.PlaybackRecord;
import de.nsn.neo.player.PlaybackEngine;
import de.nsn.neo.source.Callback;
import de.nsn.neo.source.SourceProvider;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Invisible JavaScript resolver followed by visible Media3 playback only. */
public final class PlayerActivity extends Activity {
    public static final String EXTRA_HOSTER = "hoster", EXTRA_SOURCE = "source", EXTRA_HOSTER_NAME = "hosterName";
    public static final String EXTRA_LANGUAGE = "language", EXTRA_CONTENT = "content", EXTRA_EPISODE = "episode";
    public static final String EXTRA_TITLE = "title", EXTRA_SUBTITLE = "subtitle", EXTRA_POSTER = "poster";
    private FrameLayout root;
    private WebView resolver;
    private PlaybackEngine playback;
    private final AtomicBoolean resolved = new AtomicBoolean();
    private NsnApplication application;
    private SourceId source;
    private String contentId, episodeId, title, subtitle, posterUrl, language, hosterName;
    private long resumePosition;
    private boolean keepChosenResume;
    private LinearLayout controls;
    private LinearLayout controlButtons;
    private SeekBar timeline;
    private TextView timeLabel;
    private long webPositionMs,webDurationMs;
    private View webCursor;
    private boolean challengeMode;
    /** AniWorld creates the actual hoster iframe only after a DOM click. */
    private boolean aniWorldClickPending;
    private float cursorX,cursorY;
    private final Handler uiHandler=new Handler(Looper.getMainLooper());
    private final Runnable hideControls=()->{if(controls!=null)controls.setVisibility(View.GONE);};
    private final Runnable updateProgress=new Runnable(){@Override public void run(){
        if(controls!=null&&resolved.get()){
            long position=playback.positionMs(),duration=playback.durationMs();
            if(duration>0){timeline.setProgress((int)Math.min(1000,position*1000/Math.max(1,duration)));timeLabel.setText(formatTime(position)+" / "+formatTime(duration));}
        }
        uiHandler.postDelayed(this,500);
    }};

    @SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); NsnViews.applyMobileImmersiveBars(this); getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        application=(NsnApplication)getApplication();
        String sourceName=getIntent().getStringExtra(EXTRA_SOURCE); source=readSource(sourceName);
        contentId=getIntent().getStringExtra(EXTRA_CONTENT); episodeId=getIntent().getStringExtra(EXTRA_EPISODE);
        title=getIntent().getStringExtra(EXTRA_TITLE); subtitle=getIntent().getStringExtra(EXTRA_SUBTITLE); posterUrl=getIntent().getStringExtra(EXTRA_POSTER);
        language=getIntent().getStringExtra(EXTRA_LANGUAGE);hosterName=getIntent().getStringExtra(EXTRA_HOSTER_NAME);
        if(source!=null)resumePosition=application.library().resumePosition(source,contentId,episodeId);
        root=new FrameLayout(this); root.setBackgroundColor(Color.BLACK); setContentView(root);
        playback=application.playback().main(); playback.setOnEndedListener(()->runOnUiThread(this::playNextEpisode)); playback.attach(root,true);
        addTvControls();showControls(true);uiHandler.post(updateProgress);
        String hoster=getIntent().getStringExtra(EXTRA_HOSTER);
        if(resumePosition>=5_000)showResumeChoice(hoster);else resolveHoster(hoster);
    }

    private static SourceId readSource(String value) { try { return value == null ? null : SourceId.valueOf(value); } catch (IllegalArgumentException e) { return null; } }

    private void showResumeChoice(String hoster){
        String time=formatTime(resumePosition);new AlertDialog.Builder(this).setTitle(title==null?"Wiedergabe fortsetzen":title)
            .setMessage("Zuletzt angesehen bis "+time)
            .setPositiveButton("Fortsetzen bei "+time,(dialog,which)->{keepChosenResume=true;resolveHoster(hoster);})
            .setNegativeButton("Von vorn ansehen",(dialog,which)->{resumePosition=0;keepChosenResume=true;resolveHoster(hoster);})
            .setOnCancelListener(dialog->finish()).show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void resolveHoster(String hoster){
        resolved.set(false);if(!keepChosenResume)resumePosition=source==null?0:application.library().resumePosition(source,contentId,episodeId);keepChosenResume=false;
        if (isMedia(hoster)) { play(hoster, Map.of()); return; }
        resolver=new WebView(this); resolver.setBackgroundColor(Color.BLACK); resolver.setAlpha(.01f);
        resolver.setOnTouchListener((view,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN)showControls(false);return false;});
        resolver.setFocusable(false); resolver.setFocusableInTouchMode(false); resolver.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        WebSettings settings=resolver.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setJavaScriptCanOpenWindowsAutomatically(false); settings.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(resolver,true);
        Map<String,String> initialHeaders=new HashMap<>();
        if(source!=null){
            Map<String,String> sourceHeaders=application.sources().get(source).webRequestHeaders(hoster);initialHeaders.putAll(sourceHeaders);
            if(episodeId!=null&&!episodeId.isBlank())initialHeaders.put("Referer",episodeId);
            String cookies=sourceHeaders.get("Cookie");if(cookies==null)cookies=sourceHeaders.get("cookie");
            if(cookies!=null)for(String cookie:cookies.split(";"))CookieManager.getInstance().setCookie(hoster,cookie.trim());
            CookieManager.getInstance().flush();
        }
        resolver.addJavascriptInterface(new Object(){
            @JavascriptInterface public void found(String url){if(url==null)return;String clean=url.replace("\\\\/","/").replace("\\u0026","&");android.util.Log.i("NSN_STREAM","JS candidate: "+clean);if(isMedia(clean))runOnUiThread(()->play(clean,webHeaders(clean,hoster)));}
            @JavascriptInterface public void state(double position,double duration,boolean paused){runOnUiThread(()->updateWebState(position,duration,paused));}
            @JavascriptInterface public void challenge(){runOnUiThread(PlayerActivity.this::showChallenge);}
            @JavascriptInterface public void redirect(String url){
                if(url==null||source!=SourceId.ANIWORLD)return;
                String clean=url.replace("\\\\/","/").replace("&amp;","&");
                boolean external=false;
                try { String host=Uri.parse(clean).getHost(); String base=Uri.parse(episodeId).getHost(); external=host!=null&&base!=null&&!host.equalsIgnoreCase(base); } catch(Exception ignored){}
                if(clean.contains("/redirect/")||clean.contains("/r?t=")||clean.contains("/stream/")||external){
                    android.util.Log.i("NSN_STREAM","AniWorld hoster redirect: "+clean);
                    runOnUiThread(()->{ if(resolver!=null) resolver.loadUrl(clean, webHeaders(clean, episodeId)); });
                }
            }
        },"NsnResolver");
        resolver.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request){
                String url=request.getUrl().toString(); if(isMedia(url)){android.util.Log.i("NSN_STREAM","request: "+url);runOnUiThread(()->play(url,headers(request,hoster)));return emptyMedia(url);}
                return super.shouldInterceptRequest(view,request);
            }
            @Override public void onLoadResource(WebView view,String url){if(isMedia(url))runOnUiThread(()->play(url,webHeaders(url,hoster)));}
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){
                if(source==SourceId.ANIWORLD && (url.contains("/redirect/")||url.contains("/r?t="))){
                    view.loadUrl(url,webHeaders(url,episodeId)); return true;
                }
                return false;
            }
            @Override public void onPageFinished(WebView view,String url){
                if(url.toLowerCase(Locale.ROOT).contains("captcha"))showChallenge();
                if(source==SourceId.ANIWORLD){
                    String wantedHoster=org.json.JSONObject.quote(hosterName==null?"":hosterName);
                    String wantedLanguage=org.json.JSONObject.quote(languageKey(language));
                    String clickScript="(function(){var h="+wantedHoster+",k="+wantedLanguage+";"+
                        "var box=k&&k!=='null'?document.querySelector('[data-lang-key=\\\"'+k+'\\\"]'):null;"+
                        "var roots=box?[box,document]:[document];var q='button.link-box,a.watchEpisode,.hosterSiteVideo,[data-link-id]';"+
                        "for(var r=0;r<roots.length;r++){var a=roots[r].querySelectorAll(q);for(var i=0;i<a.length;i++){"+
                        "var n=((a[i].innerText||a[i].textContent||'')+' '+(a[i].getAttribute('title')||'')).toLowerCase();"+
                        "if(!h||n.indexOf(h.toLowerCase())>=0){a[i].dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));if(a[i].click)a[i].click();"+
                        "setTimeout(function(){var f=document.querySelector('.player-wrap iframe,iframe[src*=\\\"/redirect/\\\"],iframe[src*=\\\"/r?t=\\\"]');if(f&&f.src)NsnResolver.redirect(f.src)},250);return;}}}"+
                        "})()";
                    view.evaluateJavascript(clickScript,null);
                    uiHandler.postDelayed(()->{if(resolver!=null&&!resolved.get())resolver.evaluateJavascript(clickScript,null);},700);
                    uiHandler.postDelayed(()->{if(resolver!=null&&!resolved.get())resolver.evaluateJavascript(clickScript,null);},1600);
                }
                view.evaluateJavascript("(function(){window.open=function(){return null};var sent={};function s(u){if(!u)return;u=String(u).replace(/\\\\\\//g,'/').replace(/&amp;/g,'&');if(!sent[u]&&/(m3u8|mp4|m4v|webm|\\/hls\\/|manifest)/i.test(u)){sent[u]=1;NsnResolver.found(u)}}function scan(){document.querySelectorAll('video,video source').forEach(function(v){v.muted=false;s(v.currentSrc);s(v.src);if(!v.__nsn){v.__nsn=1;setInterval(function(){NsnResolver.state(v.currentTime||0,v.duration||0,!!v.paused)},350)}v.play().catch(function(){})});try{performance.getEntriesByType('resource').forEach(function(e){s(e.name)})}catch(e){}}scan();setInterval(scan,500);setTimeout(function(){var q=['.vjs-big-play-button','.jw-icon-playback','.jw-display-icon-container','.plyr__control--overlaid','button[class*=play]','[aria-label*=Play]','[aria-label*=Abspielen]'];var b=null;for(var i=0;i<q.length&&!b;i++)b=document.querySelector(q[i]);if(!b)b=document.elementFromPoint(innerWidth/2,innerHeight/2);if(b){b.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));if(b.click)b.click()}},900)})()",null);
                view.evaluateJavascript("(function(){var t=((document.title||'')+' '+(document.body?document.body.innerText:'')).toLowerCase();if(/recaptcha|captcha|ich bin ein mensch|verify you are human/.test(t))NsnResolver.challenge()})()",null);
            }
        });
        root.addView(resolver,new FrameLayout.LayoutParams(-1,-1));if(controls!=null)controls.bringToFront();
        String resolverUrl=source==SourceId.ANIWORLD&&episodeId!=null&&!episodeId.isBlank()?episodeId:hoster;
        resolver.loadUrl(resolverUrl,initialHeaders);
    }

    private static String languageKey(String value){
        if(value==null)return "";String lower=value.toLowerCase(Locale.ROOT);
        if(lower.contains("synchron"))return "1";
        if(lower.contains("deutsch")&&lower.contains("untertitel"))return "2";
        if(lower.contains("englisch"))return "3";
        return value.matches("\\d+")?value:"";
    }

    private void playNextEpisode(){
        savePosition();if(source==null)return;SourceProvider provider=application.sources().get(source);
        provider.episodes(contentId,new Callback<List<Episode>>(){
            @Override public void onSuccess(List<Episode> values){runOnUiThread(()->{
                List<Episode> episodes=new ArrayList<>(values);episodes.sort(Comparator.comparingInt((Episode e)->e.season).thenComparingInt(e->e.number));
                int index=-1;for(int i=0;i<episodes.size();i++)if(episodeId.equals(episodes.get(i).id)){index=i;break;}if(index<0||index+1>=episodes.size())return;
                Episode next=episodes.get(index+1);episodeId=next.id;subtitle="S"+next.season+" · E"+next.number+(next.title==null?"":" · "+next.title);resumePosition=0;
                selectNextLanguage(provider,next);
            });}
            @Override public void onError(Throwable error){}
        });
    }

    private void selectNextLanguage(SourceProvider provider,Episode next){
        provider.languages(contentId,next.id,new Callback<List<String>>(){
            @Override public void onSuccess(List<String> values){String selected=values.contains(language)?language:(values.isEmpty()?null:values.get(0));language=selected;selectNextHoster(provider,next,selected);}
            @Override public void onError(Throwable error){selectNextHoster(provider,next,language);}
        });
    }
    private void selectNextHoster(SourceProvider provider,Episode next,String selectedLanguage){
        provider.hosters(contentId,next.id,selectedLanguage,new Callback<List<HosterOption>>(){
            @Override public void onSuccess(List<HosterOption> values){runOnUiThread(()->{if(values.isEmpty())return;HosterOption selected=values.get(0);for(HosterOption h:values)if(h.name.equalsIgnoreCase(hosterName)){selected=h;break;}hosterName=selected.name;playback.stop();resolveHoster(selected.url);});}
            @Override public void onError(Throwable error){}
        });
    }

    private Map<String,String> headers(WebResourceRequest request,String referer){
        Map<String,String> headers=new HashMap<>(request.getRequestHeaders()); headers.put("Referer",referer);headers.putIfAbsent("User-Agent",WebSettings.getDefaultUserAgent(this));
        try{android.net.Uri origin=android.net.Uri.parse(referer);headers.putIfAbsent("Origin",origin.getScheme()+"://"+origin.getAuthority());}catch(Exception ignored){}
        String cookie=CookieManager.getInstance().getCookie(request.getUrl().toString()); if(cookie!=null)headers.put("Cookie",cookie); return headers;
    }
    private Map<String,String> webHeaders(String url,String referer){Map<String,String> result=new HashMap<>();result.put("Referer",referer);result.put("User-Agent",WebSettings.getDefaultUserAgent(this));try{android.net.Uri origin=android.net.Uri.parse(referer);result.put("Origin",origin.getScheme()+"://"+origin.getAuthority());}catch(Exception ignored){}String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null)result.put("Cookie",cookie);return result;}
    private WebResourceResponse emptyMedia(String url){String type=url.toLowerCase(Locale.ROOT).contains("m3u8")?"application/vnd.apple.mpegurl":"video/mp4";return new WebResourceResponse(type,"UTF-8",new java.io.ByteArrayInputStream(new byte[0]));}
    private void play(String url,Map<String,String> headers){
        if(!resolved.compareAndSet(false,true))return;
        android.util.Log.i("NSN_STREAM","native handoff: "+url);
        challengeMode=false;if(webCursor!=null){root.removeView(webCursor);webCursor=null;}if(resolver!=null){resolver.stopLoading(); resolver.loadUrl("about:blank"); resolver.destroy(); resolver=null;}
        playback.prepare(new ResolvedStream(url,mime(url),headers,null),resumePosition,true);
        if(controls!=null)showControls(true);
    }
    private static boolean isMedia(String url){if(url==null)return false;String lower=url.toLowerCase(Locale.ROOT);int q=lower.indexOf('?');if(q>=0)lower=lower.substring(0,q);return lower.endsWith(".m3u8")||lower.endsWith(".mp4")||lower.endsWith(".m4v")||lower.endsWith(".webm")||lower.endsWith(".mpd")||lower.contains("/hls/")||lower.contains("manifest.m3u8");}
    private static String mime(String url){String lower=url.toLowerCase(Locale.ROOT);return lower.contains(".m3u8")?MimeTypes.APPLICATION_M3U8:lower.contains(".mpd")?MimeTypes.APPLICATION_MPD:MimeTypes.VIDEO_MP4;}
    private static String formatTime(long ms){long total=Math.max(0,ms/1000),hours=total/3600,minutes=(total%3600)/60,seconds=total%60;return String.format(Locale.GERMANY,"%02d:%02d:%02d",hours,minutes,seconds);}
    @Override public boolean onKeyDown(int keyCode,KeyEvent event){
        if(keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){togglePlayback();return true;}
        return super.onKeyDown(keyCode,event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN&&controls!=null&&!challengeMode)showControls(false);
        if(challengeMode&&event.getAction()==KeyEvent.ACTION_DOWN){
            int key=event.getKeyCode();float step=NsnViews.dp(this,26);
            if(key==KeyEvent.KEYCODE_DPAD_LEFT)cursorX-=step;else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)cursorX+=step;else if(key==KeyEvent.KEYCODE_DPAD_UP)cursorY-=step;else if(key==KeyEvent.KEYCODE_DPAD_DOWN)cursorY+=step;else if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){clickWebCursor();return true;}else return super.dispatchKeyEvent(event);
            moveWebCursor();return true;
        }
        if(de.nsn.neo.BuildConfig.IS_TV&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getKeyCode()!=KeyEvent.KEYCODE_BACK)showControls(false);
        return super.dispatchKeyEvent(event);
    }
    private void showChallenge(){
        if(resolver==null||challengeMode)return;challengeMode=true;resolver.setAlpha(1f);resolver.setFocusable(true);resolver.setFocusableInTouchMode(true);resolver.requestFocus();if(controls!=null)controls.setVisibility(View.GONE);
        cursorX=root.getWidth()/2f;cursorY=root.getHeight()/2f;webCursor=new View(this);GradientDrawable dot=new GradientDrawable();dot.setShape(GradientDrawable.OVAL);dot.setColor(Color.WHITE);dot.setStroke(NsnViews.dp(this,4),getColor(de.nsn.neo.R.color.nsn_red));webCursor.setBackground(dot);
        root.addView(webCursor,new FrameLayout.LayoutParams(NsnViews.dp(this,28),NsnViews.dp(this,28)));moveWebCursor();webCursor.bringToFront();
    }
    private void moveWebCursor(){if(webCursor==null)return;cursorX=Math.max(0,Math.min(root.getWidth()-webCursor.getWidth(),cursorX));cursorY=Math.max(0,Math.min(root.getHeight()-webCursor.getHeight(),cursorY));webCursor.setX(cursorX);webCursor.setY(cursorY);}
    private void clickWebCursor(){if(resolver==null)return;long now=android.os.SystemClock.uptimeMillis();float x=cursorX+NsnViews.dp(this,14),y=cursorY+NsnViews.dp(this,14);resolver.dispatchTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0));resolver.dispatchTouchEvent(MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,x,y,0));}
    private void addTvControls(){
        controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setGravity(android.view.Gravity.CENTER);controls.setVisibility(View.INVISIBLE);controls.setPadding(NsnViews.dp(this,de.nsn.neo.BuildConfig.IS_TV?20:8),NsnViews.dp(this,de.nsn.neo.BuildConfig.IS_TV?10:6),NsnViews.dp(this,de.nsn.neo.BuildConfig.IS_TV?20:8),NsnViews.dp(this,de.nsn.neo.BuildConfig.IS_TV?10:6));controls.setBackgroundColor(Color.argb(de.nsn.neo.BuildConfig.IS_TV?210:225,0,0,0));
        LinearLayout progressRow=new LinearLayout(this);progressRow.setGravity(android.view.Gravity.CENTER_VERTICAL);timeLabel=NsnViews.text(this,"00:00:00 / 00:00:00",de.nsn.neo.BuildConfig.IS_TV?16:13,Color.WHITE);progressRow.addView(timeLabel);timeline=new SeekBar(this);timeline.setMax(1000);timeline.getProgressDrawable().setTint(getColor(de.nsn.neo.R.color.nsn_red));timeline.getThumb().setTint(getColor(de.nsn.neo.R.color.nsn_red));timeline.setFocusable(true);timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){if(user){long duration=resolved.get()?playback.durationMs():webDurationMs;seekAbsolute(duration*value/1000);}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});LinearLayout.LayoutParams timelineParams=de.nsn.neo.BuildConfig.IS_TV?new LinearLayout.LayoutParams(NsnViews.dp(this,700),-2):new LinearLayout.LayoutParams(0,-2,1f);progressRow.addView(timeline,timelineParams);controls.addView(progressRow);
        controlButtons=new LinearLayout(this);controlButtons.setOrientation(de.nsn.neo.BuildConfig.IS_TV?LinearLayout.HORIZONTAL:LinearLayout.HORIZONTAL);controlButtons.setGravity(android.view.Gravity.CENTER);controls.addView(controlButtons,new LinearLayout.LayoutParams(-1,-2));
        addControl("10 Sek. zur\u00fcck",()->seekRelative(-10_000));
        addControl(source==SourceId.ANIWORLD?"1:28 vor":"10 Sek. vor",()->seekRelative(source==SourceId.ANIWORLD?88_000:10_000));
        addControl("Play / Pause",this::togglePlayback);
        addControl("N\u00e4chste Folge",this::playNextEpisode);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,android.view.Gravity.BOTTOM);root.addView(controls,p);
    }
    private void addControl(String label,Runnable action){TextView button=NsnViews.text(this,label,de.nsn.neo.BuildConfig.IS_TV?18:14,Color.WHITE);button.setFocusable(true);button.setClickable(true);button.setGravity(android.view.Gravity.CENTER);button.setSingleLine(true);button.setPadding(NsnViews.dp(this,de.nsn.neo.BuildConfig.IS_TV?20:10),NsnViews.dp(this,de.nsn.neo.BuildConfig.IS_TV?14:10),NsnViews.dp(this,de.nsn.neo.BuildConfig.IS_TV?20:10),NsnViews.dp(this,de.nsn.neo.BuildConfig.IS_TV?14:10));button.setBackgroundColor(Color.rgb(32,32,32));button.setOnFocusChangeListener((v,f)->{v.setBackgroundColor(f?Color.rgb(235,0,35):Color.rgb(32,32,32));v.animate().scaleX(f?1.04f:1f).scaleY(f?1.04f:1f).setDuration(100).start();});button.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=de.nsn.neo.BuildConfig.IS_TV?new LinearLayout.LayoutParams(-2,-2):new LinearLayout.LayoutParams(0,-2,1f);lp.setMargins(NsnViews.dp(this,2),0,NsnViews.dp(this,2),0);controlButtons.addView(button,lp);}
    private void showControls(boolean focusPlay){if(controls==null)return;controls.setVisibility(View.VISIBLE);controls.bringToFront();if(focusPlay&&!controlButtons.hasFocus())controlButtons.getChildAt(2).requestFocus();uiHandler.removeCallbacks(hideControls);uiHandler.postDelayed(hideControls,4500);}
    private void updateWebState(double position,double duration,boolean paused){if(resolved.get()||duration<=0)return;webPositionMs=(long)(position*1000);webDurationMs=(long)(duration*1000);resolver.setAlpha(1f);if(controls!=null){showControls(false);timeline.setProgress((int)Math.min(1000,webPositionMs*1000/Math.max(1,webDurationMs)));timeLabel.setText(formatTime(webPositionMs)+" / "+formatTime(webDurationMs));if(!controlButtons.hasFocus())controlButtons.getChildAt(2).requestFocus();}}
    private void seekRelative(long delta){long base=resolved.get()?playback.positionMs():webPositionMs;seekAbsolute(base+delta);}
    private void seekAbsolute(long position){long target=Math.max(0,position);if(resolved.get())playback.seekTo(target);else if(resolver!=null)resolver.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){v.currentTime="+(target/1000d)+"})",null);}
    private void togglePlayback(){if(resolved.get()){if(playback.isPlaying())playback.pause();else playback.play();}else if(resolver!=null)resolver.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){if(v.paused)v.play();else v.pause()})",null);}
    @Override protected void onStop(){savePosition();if(playback!=null)playback.pause();getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onStop();}
    private void savePosition(){if(playback==null||source==null)return;application.library().savePlayback(new PlaybackRecord(source,contentId,episodeId,title,subtitle,posterUrl,playback.positionMs(),playback.durationMs(),System.currentTimeMillis()));}
    @Override protected void onDestroy(){uiHandler.removeCallbacksAndMessages(null);if(playback!=null)playback.detach();if(resolver!=null){resolver.stopLoading();resolver.destroy();}super.onDestroy();}
}

