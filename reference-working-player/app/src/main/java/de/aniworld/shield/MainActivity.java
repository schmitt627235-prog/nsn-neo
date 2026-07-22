package de.serienstreams.neo.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.Scanner;
import java.util.HashMap;

public class MainActivity extends Activity {
    private static final String HOME = "http://186.2.175.5/";
    private WebView webView;
    private ProgressBar progress;
    private TextView shieldStatus;
    private boolean blockerEnabled = true;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout root;
    private LinearLayout navigationBar;
    private String pendingTrailerTitle;
    private View playerCursor;
    private boolean playerCursorActive;
    private float cursorX, cursorY;
    private WebView playerWebView;
    private SurfaceView nativeVideo;
    private MediaPlayer nativePlayer;
    private String nativeStreamUrl;
    private volatile String pendingNativeStreamUrl;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        configureWebView();
        if (state == null) webView.loadUrl(HOME); else webView.restoreState(state);
    }

    private Button button(String text, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(18);
        b.setBackgroundResource(de.serienstreams.neo.tv.R.drawable.nav_button); b.setOnClickListener(action);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setAllCaps(false);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, dp(52), 1));
        return b;
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(5,5,5));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(5,5,5));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                webView.evaluateJavascript("(function(){var e=document.activeElement;return !!(e&&(/^(INPUT|TEXTAREA|SELECT)$/.test(e.tagName)||e.isContentEditable))})()", result -> {
                    if ("true".equals(result)) {
                        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        if (keyboard != null) keyboard.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
                    }
                });
            }
            return false;
        });
        column.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        column.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER); bar.setBackgroundColor(Color.rgb(5,5,5));
        bar.addView(button("‹", v -> { if (webView.canGoBack()) webView.goBack(); }));
        bar.addView(button("›", v -> { if (webView.canGoForward()) webView.goForward(); }));
        bar.addView(button("⌂", v -> webView.loadUrl(HOME)));
        shieldStatus = new TextView(this);
        shieldStatus.setGravity(Gravity.CENTER); shieldStatus.setTextSize(13); shieldStatus.setTextColor(Color.rgb(109,203,76));
        shieldStatus.setText("SCHUTZ AN"); shieldStatus.setFocusable(true); shieldStatus.setBackgroundResource(de.serienstreams.neo.tv.R.drawable.nav_button); shieldStatus.setOnClickListener(v -> toggleBlocker());
        shieldStatus.setLayoutParams(new LinearLayout.LayoutParams(0, dp(52), 1.35f));
        bar.addView(shieldStatus);
        bar.addView(button("↻", v -> webView.reload()));
        column.addView(bar);
        navigationBar = bar;
        navigationBar.setVisibility(View.GONE);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        playerCursor = new View(this);
        GradientDrawable cursorShape = new GradientDrawable();
        cursorShape.setShape(GradientDrawable.OVAL);
        cursorShape.setColor(Color.WHITE);
        cursorShape.setStroke(dp(3), Color.rgb(255, 16, 40));
        playerCursor.setBackground(cursorShape);
        playerCursor.setElevation(dp(30));
        playerCursor.setVisibility(View.GONE);
        root.addView(playerCursor, new FrameLayout.LayoutParams(dp(20), dp(20)));
        setContentView(root);
    }

    @SuppressLint("SetJavaScriptEnabled") private void configureWebView() {
        AdBlocker.init(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString() + " AniWorldShield/1.0");
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void showCursor() { runOnUiThread(() -> showPlayerCursor()); }
            @JavascriptInterface public void hideCursor() { runOnUiThread(() -> hidePlayerCursor()); }
            @JavascriptInterface public void openPlayer(String url) { runOnUiThread(() -> openNativePlayer(url)); }
        }, "NeoRemote");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String requestUrl = request.getUrl().toString();
                // The WebView is used only to resolve the hoster page. As soon as it
                // requests a direct HLS/MP4 stream, block that request in WebView and hand
                // the URL to the single native player. This prevents double audio and the
                // black SurfaceView-over-WebView problem seen on Fire TV.
                if (isDirectMediaUrl(requestUrl)) {
                    launchNativeStreamOnce(requestUrl, request.getRequestHeaders().get("Referer"));
                    return emptyMediaResponse(requestUrl);
                }
                if ("neo.local".equals(request.getUrl().getHost())) {
                    int image = request.getUrl().getPath().contains("banner") ? R.drawable.neo_banner : R.drawable.neo_logo;
                    return new WebResourceResponse("image/png", null, getResources().openRawResource(image));
                }
                return AdBlocker.shouldBlock(request.getUrl().toString(), blockerEnabled)
                    ? AdBlocker.emptyResponse() : super.shouldInterceptRequest(view, request);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                if ("aniflix".equals(scheme) && "trailer".equals(u.getHost())) {
                    pendingTrailerTitle = u.getQueryParameter("title");
                    view.loadUrl("https://aniflix.uno/search/");
                    return true;
                }
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    if (request.isForMainFrame() && !isAllowedMainFrame(u)) {
                        Toast.makeText(MainActivity.this, "Werbe-Pop-up blockiert", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    if (request.isForMainFrame()) view.loadUrl(u.toString());
                    return request.isForMainFrame();
                }
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap icon) {
                view.animate().cancel();
                view.setAlpha(0f);
                view.setBackgroundColor(Color.rgb(5,5,5));
                progress.setVisibility(View.VISIBLE);
                view.evaluateJavascript("window.open=function(){return null};", null);
            }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (blockerEnabled) injectCosmeticFilters(view);
                if (blockerEnabled) injectOverlayBlocker(view);
                injectNeoHome(view);
                injectNeoCatalog(view);
                injectNeoDetailV2(view);
                injectPartnerNavigation(view);
                injectPlayerControls(view);
                injectTrailerIntegration(view, url);
                injectTvFocus(view);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { progress.setProgress(value); }
            @Override public boolean onCreateWindow(WebView v, boolean dialog, boolean gesture, android.os.Message resultMsg) {
                Toast.makeText(MainActivity.this, "Pop-up blockiert", Toast.LENGTH_SHORT).show();
                return false;
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (playerCursorActive) {
                    callback.onCustomViewHidden();
                    Toast.makeText(MainActivity.this, "Fire-TV-Vollbild blockiert – Player bleibt im kompatiblen Fenstermodus", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view; customViewCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                webView.setVisibility(View.GONE);
            }
            @Override public void onHideCustomView() { hideCustomView(); }
        });
    }

    private void injectCosmeticFilters(WebView view) {
        String js = "(function(){var s=document.getElementById('aws-filter');if(!s){s=document.createElement('style');s.id='aws-filter';s.textContent='" +
            "[id*=\\\"ad-\\\"],[class*=\\\"ad-container\\\"],[class*=\\\"advert\\\"],iframe[src*=\\\"doubleclick\\\"],iframe[src*=\\\"facebook.com/plugins\\\"],iframe[src*=\\\"platform.twitter.com\\\"]{display:none!important}" +
            "html,body{background:#050505!important;color:#ededed!important;font-family:Arial,sans-serif!important}" +
            ".main-header{display:none!important}#wrapper{padding-top:76px!important}" +
            "#neo-nav{position:fixed!important;left:0!important;right:0!important;top:0!important;height:76px!important;z-index:10000!important;display:flex!important;align-items:center!important;gap:24px!important;padding:0 34px!important;background:rgba(5,5,5,.96)!important;border-bottom:1px solid #202020!important;box-sizing:border-box!important}" +
            "#neo-nav a{color:#fff!important;text-decoration:none!important;font-weight:700!important}#neo-nav .neoBrand{display:flex!important;align-items:center!important}.neoBrand img{display:block!important;width:62px!important;height:62px!important;object-fit:cover!important;border-radius:11px!important}#neo-nav .neoMenu,#neo-nav .neoSearch{font-size:31px!important}#neo-nav .neoSpacer{flex:1!important}#neo-nav .neoLogin{background:#f2164b!important;padding:12px 19px!important;border-radius:11px!important}" +
            ".primary-navigation a,.main-header a{color:#fff!important}" +
            "h1,h2,h3,h4{color:#fff!important;font-weight:700!important}" +
            "a{transition:.2s!important}a:hover{color:#f2164b!important}" +
            "main img,.seriesListContainer img,.coverListItem img{border-radius:16px!important}" +
            ".seriesListContainer>div,.coverListItem,.latestEpisode,.calendarEntry{background:#111!important;border:1px solid #242424!important;border-radius:16px!important;overflow:hidden!important}" +
            "button,.btn,input[type=submit]{border-radius:16px!important;background:#f2164b!important;color:#fff!important;border:0!important}" +
            "input,select,textarea{background:#1e1b1b!important;color:#fff!important;border:1px solid #333!important;border-radius:16px!important}" +
            ".footer-container{background:#090909!important;border-top:1px solid #242424!important}" +
            ".hosterSiteVideo,.streamingPlayer{border-radius:16px!important;overflow:hidden!important;background:#000!important}" +
            ".trailerButton,.neoTrailerButton{display:inline-flex!important;align-items:center!important;gap:10px!important;background:#f2164b!important;color:#fff!important;padding:13px 18px!important;border-radius:16px!important;font-weight:700!important;margin:10px 0!important;text-decoration:none!important}" +
            "#neo-spotlight{position:relative!important;min-height:70vh!important;overflow:hidden!important;background:#050505!important;color:#fff!important;margin:0 0 28px!important}" +
            "#neo-spotlight iframe,#neo-spotlight .neoPoster{position:absolute!important;inset:0!important;width:100%!important;height:100%!important;border:0!important;object-fit:cover!important;pointer-events:none!important}" +
            "#neo-spotlight .neoShade{position:absolute!important;inset:0!important;background:linear-gradient(90deg,rgba(0,0,0,.94) 0%,rgba(0,0,0,.62) 42%,rgba(0,0,0,.15) 75%),linear-gradient(0deg,#050505 0%,transparent 45%)!important}" +
            "#neo-spotlight .neoCopy{position:relative!important;z-index:2!important;max-width:720px!important;padding:14vh 6vw 7vh!important}" +
            "#neo-spotlight h1{font-size:52px!important;margin:0 0 16px!important}#neo-spotlight p{font-size:21px!important;line-height:1.5!important;display:-webkit-box!important;-webkit-line-clamp:4!important;-webkit-box-orient:vertical!important;overflow:hidden!important}" +
            "#neo-spotlight .neoActions{display:flex!important;gap:16px!important}#neo-spotlight .neoActions a{padding:16px 24px!important;border-radius:14px!important;background:#f2164b!important;color:#fff!important;font-weight:700!important;text-decoration:none!important}#neo-spotlight .neoActions a+ a{background:#333!important}" +
            ".carousel:not(.animeNews)>.row,.carousel:not(.animeNews) .homeSliderView{display:grid!important;grid-template-columns:repeat(auto-fill,minmax(200px,1fr))!important;gap:24px!important}.carousel:not(.animeNews)>.row>[class*=col-],.carousel:not(.animeNews) .homeSliderView>[class*=col-]{width:auto!important;max-width:none!important;padding:0!important;float:none!important}" +
            "a[href*=\\\"/anime/stream/\\\"] img{border-radius:16px!important;transition:transform .25s!important}a[href*=\\\"/anime/stream/\\\"]:focus img{transform:scale(1.05)!important;outline:4px solid #f2164b!important}.pageTitle15 h1,.pageTitle15 h2{font-size:28px!important;border-left:5px solid #f2164b!important;padding-left:14px!important}" +
            "#footer,.footer-container,.shoutbox{display:none!important}#wrapper>.container{max-width:1600px!important;background:#050505!important}" +
            "';document.documentElement.appendChild(s)}document.querySelectorAll('.trailerButton').forEach(function(a){a.target='_self';a.setAttribute('aria-label','Trailer ansehen')});if((location.hostname==='186.2.175.111'||location.hostname==='aniworld.to'||location.hostname.endsWith('.aniworld.to'))&&!document.getElementById('neo-nav')){var n=document.createElement('nav');n.id='neo-nav';n.innerHTML='<a class=\\\"neoMenu\\\" href=\\\"#\\\" aria-label=\\\"Menü öffnen\\\">☰</a><a class=\\\"neoBrand\\\" href=\\\"/\\\" aria-label=\\\"AniWorld Neo Startseite\\\"><img src=\\\"https://neo.local/logo.png\\\" alt=\\\"AniWorld Neo\\\"></a><span class=\\\"neoSpacer\\\"></span><a class=\\\"neoSearch\\\" href=\\\"/search\\\" aria-label=\\\"Suche\\\">⌕</a><a class=\\\"neoLogin\\\" href=\\\"/login\\\">Login</a>';document.body.appendChild(n)}document.documentElement.lang='de';})()";
        view.evaluateJavascript(js, null);
    }

    private void injectTrailerIntegration(WebView view, String url) {
        if (url == null) return;
        if (url.startsWith("http://186.2.175.5/") || url.startsWith("https://186.2.175.5/")) {
            String js = "(function(){if(location.pathname==='/'&&!document.getElementById('neo-spotlight')){" +
                "var links=[].slice.call(document.querySelectorAll('a[href*=\\\"/anime/stream/\\\"]')).filter(function(a){try{return /^\\/anime\\/stream\\/[^\\/]+\\/?$/.test(new URL(a.href,location.href).pathname)}catch(e){return false}});var a=links[0];if(a){fetch(a.href,{credentials:'include'}).then(function(r){return r.text()}).then(function(t){var d=new DOMParser().parseFromString(t,'text/html'),tr=d.querySelector('.trailerButton'),u=tr&&tr.href,id='';if(u){try{var x=new URL(u);id=x.searchParams.get('v')||(/youtu\\.be$/.test(x.hostname)?x.pathname.slice(1):(/\\/embed\\//.test(x.pathname)?x.pathname.split('/embed/')[1].split('/')[0]:''))}catch(e){}}" +
                "var s=document.createElement('section');s.id='neo-spotlight';var im=a.querySelector('img');if(im){var p=document.createElement('img');p.className='neoPoster';p.src=im.currentSrc||im.src;s.appendChild(p)}if(id){var f=document.createElement('iframe');f.allow='autoplay; encrypted-media; picture-in-picture';f.src='https://www.youtube-nocookie.com/embed/'+encodeURIComponent(id)+'?autoplay=1&mute=1&controls=0&loop=1&playlist='+encodeURIComponent(id)+'&playsinline=1&rel=0';s.appendChild(f)}" +
                "var sh=document.createElement('div');sh.className='neoShade';s.appendChild(sh);var c=document.createElement('div');c.className='neoCopy';var h=document.createElement('h1');h.textContent=(d.querySelector('h1')||a).textContent.trim();c.appendChild(h);var ds=d.querySelector('[itemprop=\\\"description\\\"]');if(ds){var p2=document.createElement('p');p2.textContent=ds.getAttribute('content')||ds.textContent.trim();c.appendChild(p2)}var ac=document.createElement('div');ac.className='neoActions';var w=document.createElement('a');w.href=a.href;w.textContent='▶ Jetzt ansehen';ac.appendChild(w);var de=document.createElement('a');de.href=a.href;de.textContent='Details';ac.appendChild(de);c.appendChild(ac);s.appendChild(c);var header=document.querySelector('header');if(header)header.insertAdjacentElement('afterend',s);else document.body.insertBefore(s,document.body.firstChild)}).catch(function(){})}}" +
                "if(!/^\\/anime\\/stream\\/[^\\/]+\\/?$/.test(location.pathname))return;}if(!/^\\/anime\\/stream\\/[^\\/]+\\/?$/.test(location.pathname)||document.getElementById('neo-aniflix-trailer'))return;" +
                "var h=document.querySelector('h1');if(!h)return;var title=(h.textContent||'').trim();if(!title)return;" +
                "var a=document.createElement('a');a.id='neo-aniflix-trailer';a.className='neoTrailerButton';a.textContent='▶ Trailer bei AniFlix';" +
                "a.href='aniflix://trailer?title='+encodeURIComponent(title);a.setAttribute('aria-label','Trailer bei AniFlix suchen');" +
                "h.parentNode.insertBefore(a,h.nextSibling);})()";
            view.evaluateJavascript(js, null);
            return;
        }
        if (url.startsWith("https://aniflix.uno/")) {
            String title = JSONObject.quote(pendingTrailerTitle == null ? "" : pendingTrailerTitle);
            String js = "(function(){" +
                "function privacy(){document.querySelectorAll('iframe').forEach(function(f){var s=f.getAttribute('src')||'';if(s.indexOf('youtube.com/embed/')>=0)f.src=s.replace('www.youtube.com/embed/','www.youtube-nocookie.com/embed/').replace('youtube.com/embed/','youtube-nocookie.com/embed/')});}" +
                "privacy();new MutationObserver(privacy).observe(document.documentElement,{childList:true,subtree:true});" +
                "var q=" + title + ";if(q&&location.pathname.indexOf('/search')===0){function fill(){var i=document.querySelector('input[placeholder*=\\\"What do you want\\\"],input[type=search],input[type=text]');if(!i)return;var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');if(d&&d.set)d.set.call(i,q);else i.value=q;i.dispatchEvent(new Event('input',{bubbles:true}));i.dispatchEvent(new Event('change',{bubbles:true}));}setTimeout(fill,300);setTimeout(fill,1200);}" +
                "document.querySelectorAll('button').forEach(function(b){if((b.getAttribute('aria-label')||'').toLowerCase()==='close'&&(b.parentElement&&/ready to serve/i.test(b.parentElement.textContent||'')))b.click()});" +
                "})()";
            view.evaluateJavascript(js, null);
        }
    }

    private void injectNeoCatalog(WebView view) {
        String js = "(function(){if(document.getElementById('neo-app'))return;var p=location.pathname;if(!/^\\/(animes|animekalender|katalog|genre|neu|beliebte-animes|search)/.test(p))return;" +
            "var seen={},items=[];document.querySelectorAll('a[href*=\\\"/anime/stream/\\\"]').forEach(function(a){try{var u=new URL(a.href,location.href),im=a.querySelector('.homeContentPromotionBoxPicture>img,.seriesListHorizontalCover>img,img[alt$=\\\" Cover\\\"]');if(!im||seen[u.pathname])return;var title=(a.querySelector('h3')||{}).textContent||(a.getAttribute('title')||'').replace(/ als Stream anschauen$/i,'')||(im.getAttribute('alt')||'').replace(/ Cover$/i,'');if(!title.trim())return;seen[u.pathname]=1;items.push({a:a,im:im,u:u,title:title.trim()})}catch(e){}});if(items.length<3)return;" +
            "var st=document.createElement('style');st.id='neo-app-style';st.textContent='body.neo-rendered #wrapper>.container{display:none!important}body.neo-rendered .liveNotificationContainer{display:none!important}#neo-app{max-width:1720px;margin:auto;padding:42px 5vw 110px;box-sizing:border-box;background:#050505;color:#fff}#neo-app h1{font-size:42px;margin:0 0 32px;border-left:7px solid #f2164b;padding-left:18px}.neoGrid{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:30px 24px}.neoCard{display:block;color:#fff!important;text-decoration:none!important}.neoCard:focus{outline:6px solid #fff;outline-offset:7px;border-radius:15px}.neoCard img{display:block;width:100%;aspect-ratio:2/3;object-fit:cover;border-radius:15px;box-shadow:0 12px 30px rgba(0,0,0,.6)}.neoCardTitle{font-size:22px;font-weight:700;line-height:1.3;margin-top:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}';document.head.appendChild(st);" +
            "var app=document.createElement('main');app.id='neo-app';var h=document.createElement('h1');h.textContent=p==='/'?'Anime entdecken':((document.querySelector('h1,h2')||{}).textContent||'Alle Anime').trim();app.appendChild(h);var g=document.createElement('div');g.className='neoGrid';items.slice(0,120).forEach(function(x){var c=document.createElement('a');c.className='neoCard';c.href=x.u.href;var im=document.createElement('img');im.loading='lazy';im.src=x.im.getAttribute('data-src')||x.im.currentSrc||x.im.src;im.alt=x.title;c.appendChild(im);var t=document.createElement('div');t.className='neoCardTitle';t.textContent=x.title;c.appendChild(t);g.appendChild(c)});app.appendChild(g);document.body.classList.add('neo-rendered');(document.getElementById('wrapper')||document.body).appendChild(app)})()";
        view.evaluateJavascript(js, null);
    }

    private void injectNeoDetailV2(WebView view) {
        view.evaluateJavascript(rawScript(R.raw.neo_detail), null);
    }

    private void injectNeoDetail(WebView view) {
        String js = "(function(){if(document.getElementById('neo-detail')||!/^\\/anime\\/stream\\/[^\\/]+/.test(location.pathname))return;var h=document.querySelector('h1[itemprop=name]'),cover=document.querySelector('img[itemprop=image]');if(!h||!cover)return;var st=document.createElement('style');st.textContent='body.neo-detail-rendered #wrapper>.container{display:none!important}#neo-detail{max-width:1720px;margin:auto;padding-bottom:120px;color:#fff;background:#050505}.neoHero{position:relative;min-height:650px;display:flex;align-items:flex-end;overflow:hidden}.neoHeroBg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;filter:blur(12px);transform:scale(1.08);opacity:.42}.neoHeroShade{position:absolute;inset:0;background:linear-gradient(90deg,#050505 4%,rgba(5,5,5,.6) 58%,rgba(5,5,5,.2)),linear-gradient(0deg,#050505,transparent 55%)}.neoHeroContent{position:relative;z-index:2;display:grid;grid-template-columns:250px 1fr;gap:40px;align-items:end;padding:70px 5vw;width:100%;box-sizing:border-box}.neoCover{width:250px;aspect-ratio:2/3;object-fit:cover;border-radius:20px;box-shadow:0 20px 50px #000}.neoInfo h1{font-size:70px;margin:0 0 18px}.neoInfo p{max-width:950px;font-size:24px;line-height:1.5}.neoBadges,.neoActions,.neoEpisodeGrid,.neoHostGrid{display:flex;gap:16px;flex-wrap:wrap}.neoBadge,.neoEpisode,.neoHost,.neoAction{background:#252525;color:#fff!important;padding:16px 22px;border-radius:14px;text-decoration:none!important;font-size:22px;font-weight:700}.neoAction{background:#f2164b}.neoBody{padding:10px 5vw}.neoBody h2{font-size:36px;margin:45px 0 24px;border-left:7px solid #f2164b;padding-left:18px}.neoEpisode,.neoHost{background:#171717;border:2px solid #444}.neoEpisode:focus,.neoHost:focus,.neoAction:focus{outline:6px solid #fff;outline-offset:6px}.neoPlayer{margin-top:30px;aspect-ratio:16/9;background:#000;border-radius:20px;overflow:hidden}.neoPlayer iframe{width:100%!important;height:100%!important;border:0!important;position:static!important}';document.head.appendChild(st);var src=cover.getAttribute('data-src')||cover.currentSrc||cover.src,title=h.textContent.trim(),desc=document.querySelector('.seri_des'),app=document.createElement('main');app.id='neo-detail';var hero=document.createElement('section');hero.className='neoHero';hero.innerHTML='<img class=\\\"neoHeroBg\\\"><div class=\\\"neoHeroShade\\\"></div><div class=\\\"neoHeroContent\\\"><img class=\\\"neoCover\\\"><div class=\\\"neoInfo\\\"><h1></h1><div class=\\\"neoBadges\\\"></div><p></p><div class=\\\"neoActions\\\"></div></div></div>';hero.querySelector('.neoHeroBg').src=src;hero.querySelector('.neoCover').src=src;hero.querySelector('h1').textContent=title;hero.querySelector('p').textContent=(desc&&(desc.getAttribute('data-full-description')||desc.textContent)||'').trim();document.querySelectorAll('.genres a,.genre a').forEach(function(x){var b=document.createElement('span');b.className='neoBadge';b.textContent=x.textContent.trim();if(b.textContent)hero.querySelector('.neoBadges').appendChild(b)});var tr=document.querySelector('.trailerButton');if(tr){var ta=document.createElement('a');ta.className='neoAction';ta.href=tr.href;ta.textContent='▶ Trailer';hero.querySelector('.neoActions').appendChild(ta)}app.appendChild(hero);var body=document.createElement('div');body.className='neoBody';var base=location.pathname.match(/^(\\/anime\\/stream\\/[^\\/]+)/)[1],eps={},epLinks=[];document.querySelectorAll('a[href*=\\\"/anime/stream/\\\"]').forEach(function(a){try{var u=new URL(a.href,location.href);if(u.pathname.indexOf(base)!==0||eps[u.pathname]||!/(staffel-|episode-)/.test(u.pathname))return;eps[u.pathname]=1;epLinks.push({u:u,t:(a.innerText||'').trim()})}catch(e){}});if(epLinks.length){body.insertAdjacentHTML('beforeend','<h2>Staffeln & Episoden</h2>');var eg=document.createElement('div');eg.className='neoEpisodeGrid';epLinks.slice(0,100).forEach(function(x){var a=document.createElement('a');a.className='neoEpisode';a.href=x.u.href;a.textContent=x.t||x.u.pathname.split('/').slice(-2).join(' · ').replace(/-/g,' ');eg.appendChild(a)});body.appendChild(eg)}var player=document.createElement('div');player.className='neoPlayer';var old=document.querySelector('iframe[src*=\\\"/redirect/\\\"]');if(old)player.appendChild(old);var hosts=Array.from(document.querySelectorAll('a.watchEpisode'));if(hosts.length){body.insertAdjacentHTML('beforeend','<h2>Stream auswählen</h2>');var hg=document.createElement('div');hg.className='neoHostGrid';hosts.slice(0,12).forEach(function(x){var a=document.createElement('a');a.className='neoHost';a.href=x.href;a.textContent=(x.querySelector('h4')||x).textContent.trim();a.onclick=function(e){e.preventDefault();var f=player.querySelector('iframe');if(!f){f=document.createElement('iframe');f.allowFullscreen=true;player.appendChild(f)}f.src=x.href;player.scrollIntoView({behavior:'smooth'});return false};hg.appendChild(a)});body.appendChild(hg)}if(old||hosts.length)body.appendChild(player);app.appendChild(body);document.body.classList.add('neo-detail-rendered');(document.getElementById('wrapper')||document.body).appendChild(app)})()";
        view.evaluateJavascript(js, null);
    }

    private void injectNeoHome(WebView view) {
        view.evaluateJavascript(rawScript(R.raw.neo_home), null);
    }

    private void injectTvFocus(WebView view) {
        String js = "(function(){if(window.__neoTvDpad)return;window.__neoTvDpad=1;" +
            "var s=document.createElement('style');s.id='neo-tv-focus';s.textContent='a:focus,button:focus,input:focus,textarea:focus,select:focus,iframe:focus,[tabindex]:focus,.neoTvActive{outline:6px solid #ff1028!important;outline-offset:4px!important;box-shadow:0 0 0 3px rgba(255,16,40,.38),0 0 24px rgba(255,16,40,.9)!important;border-radius:12px!important}.neoRailCard:focus img,.neoRailCard.neoTvActive img,.neoRecommendationRail a:focus img,.neoRecommendationRail a.neoTvActive img{outline:none!important;box-shadow:none!important}.neoRailCard.neoTvActive,.neoRecommendationRail a.neoTvActive{padding:6px!important;box-sizing:border-box!important}.neoSpotlightActions .neoTvActive,.neoSpotlightNav .neoTvActive,.neoHostRail .neoTvActive,.neoLanguageTabs .neoTvActive,.neoSeasonTabs .neoTvActive{background:#ff1028!important;color:#fff!important}';document.head.appendChild(s);" +
            "function all(){return [].slice.call(document.querySelectorAll('a[href],button,input,textarea,select,iframe,[contenteditable=true],[tabindex]')).filter(function(e){var r=e.getBoundingClientRect(),c=getComputedStyle(e);return r.width>3&&r.height>3&&c.display!=='none'&&c.visibility!=='hidden'&&!e.disabled&&e.tabIndex!==-1})}" +
            "function mark(e){if(!e)return;document.querySelectorAll('.neoTvActive').forEach(function(x){x.classList.remove('neoTvActive')});e.classList.add('neoTvActive');try{e.focus({preventScroll:true})}catch(x){e.focus()}e.scrollIntoView({behavior:'smooth',block:'nearest',inline:'center'})}" +
            "document.addEventListener('focusin',function(e){if(e.target&&e.target.matches('a,button,input,textarea,select,iframe,[tabindex]'))mark(e.target)},true);" +
            "document.addEventListener('keydown',function(ev){var k=ev.key;if(!/^Arrow(Left|Right|Up|Down)$/.test(k))return;var active=document.querySelector('.neoTvActive')||document.activeElement,tag=active&&active.tagName;if(/INPUT|TEXTAREA|SELECT/.test(tag||''))return;var list=all();if(!list.length)return;if(!active||list.indexOf(active)<0){ev.preventDefault();mark(list[0]);return}var r=active.getBoundingClientRect(),cx=r.left+r.width/2,cy=r.top+r.height/2,best=null,score=1e12;list.forEach(function(e){if(e===active)return;var q=e.getBoundingClientRect(),x=q.left+q.width/2,y=q.top+q.height/2,dx=x-cx,dy=y-cy,ok=k==='ArrowRight'?dx>8:k==='ArrowLeft'?dx<-8:k==='ArrowDown'?dy>8:dy<-8;if(!ok)return;var primary=(k==='ArrowRight'||k==='ArrowLeft')?Math.abs(dx):Math.abs(dy),secondary=(k==='ArrowRight'||k==='ArrowLeft')?Math.abs(dy):Math.abs(dx),sc=primary+secondary*3;if(sc<score){score=sc;best=e}});if(best){ev.preventDefault();mark(best)}},true);" +
            "document.addEventListener('keydown',function(ev){if((ev.key==='Enter'||ev.key===' ')&&document.querySelector('.neoTvActive')){var e=document.querySelector('.neoTvActive');if(e.tagName==='IFRAME'){e.focus();return}if(!/INPUT|TEXTAREA|SELECT/.test(e.tagName)){ev.preventDefault();e.click()}}},true);setTimeout(function(){var a=all();if(a.length&&!document.querySelector('.neoTvActive'))mark(a[0])},300)})()";
        view.evaluateJavascript(js, ignored -> view.post(() -> { view.evaluateJavascript("window.scrollTo(0,0);document.documentElement.style.background='#050505';document.body.style.background='#050505';", null); view.animate().alpha(1f).setDuration(100).start(); }));
    }

    private String rawScript(int resource) {
        try (Scanner scanner = new Scanner(getResources().openRawResource(resource), "UTF-8").useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private void injectNeoNavigation(WebView view) {
        String js = "(function(){var nav=document.getElementById('neo-nav');if(!nav||document.getElementById('neo-drawer'))return;var original=document.querySelector('.main-header'),logged=!!(original&&original.querySelector('a[href*=\\\"logout\\\"]'));var d=document.createElement('aside');d.id='neo-drawer';d.innerHTML='<a href=\\\"/\\\">⌂ Startseite</a><a href=\\\"/animes\\\">▶ Alle Anime</a><a href=\\\"/beliebte-animes\\\">★ Populär</a><a href=\\\"/animekalender\\\">▣ Kalender</a><a href=\\\"/search\\\">⌕ Suche</a>'+(logged?'<hr><a href=\\\"/account\\\">☰ Account</a><a href=\\\"/profil\\\">● Profil</a><a href=\\\"/messages\\\">✉ Nachrichten</a><a href=\\\"/support\\\">⚑ Support</a><a href=\\\"/watchlist\\\">◉ WatchList</a><a href=\\\"/subscriptions\\\">◖ Abonniert</a><a href=\\\"/settings\\\">⚙ Einstellungen</a><a href=\\\"/logout\\\">↪ Logout</a>':'<hr><a href=\\\"/login\\\">Login</a><a href=\\\"/register\\\">Registrieren</a>');document.body.appendChild(d);var st=document.createElement('style');st.textContent='#neo-drawer{display:none;position:fixed;z-index:11000;left:0;top:82px;bottom:0;width:390px;overflow:auto;background:#101b22;padding:22px;box-shadow:12px 0 35px #000;box-sizing:border-box}#neo-drawer.open{display:block}#neo-drawer a{display:block;color:#fff!important;text-decoration:none;padding:18px 15px;border-radius:10px;font-size:23px}#neo-drawer a:focus{background:#20303a;outline:4px solid #fff}#neo-drawer hr{border:0;border-top:1px solid #34434c}';document.head.appendChild(st);var m=nav.querySelector('.neoMenu'),account=nav.querySelector('.neoLogin');m.onclick=function(e){e.preventDefault();d.classList.toggle('open')};if(logged){account.textContent='Konto';account.href='#';account.onclick=function(e){e.preventDefault();d.classList.toggle('open')}}})()";
        view.evaluateJavascript(js, null);
    }

    private void injectPartnerNavigation(WebView view) {
        String js = "(function(){if(document.getElementById('neo-nav'))return;var n=document.createElement('nav');n.id='neo-nav';n.innerHTML='<a class=\\\"neoMenu\\\" href=\\\"#\\\">☰</a><a class=\\\"neoBrand\\\" href=\\\"/\\\" aria-label=\\\"SerienStreams Neo Startseite\\\"><img src=\\\"https://neo.local/logo.png\\\" alt=\\\"SerienStreams Neo\\\"></a><span class=\\\"neoSpacer\\\"></span><a class=\\\"neoSearch\\\" href=\\\"/search\\\">⌕</a><a class=\\\"neoLogin\\\" href=\\\"/login\\\">Login</a>';document.body.appendChild(n)})()";
        view.evaluateJavascript(js, null);
    }

    private void injectPlayerControls(WebView view) {
        String js = "(function(){document.querySelectorAll('a.watchEpisode').forEach(function(a){a.target='_self';a.onclick=function(e){e.preventDefault();if(window.NeoRemote)NeoRemote.showCursor();var f=document.querySelector('iframe[src*=\\\"/redirect/\\\"]');if(!f){f=document.createElement('iframe');f.setAttribute('allowfullscreen','true');f.style.cssText='width:100%;aspect-ratio:16/9;border:0;background:#000';var box=document.querySelector('.hosterSiteVideo,.streamingPlayer,.hosterSite');(box||a.parentElement).insertBefore(f,(box||a.parentElement).firstChild)}f.src=a.href;f.scrollIntoView({behavior:'smooth',block:'center'});return false}})})()";
        view.evaluateJavascript(js, null);
    }

    private void showPlayerCursor() {
        int[] location = new int[2]; webView.getLocationInWindow(location);
        View target = playerWebView != null ? playerWebView : webView;
        target.getLocationInWindow(location);
        cursorX = location[0] + target.getWidth() * 0.5f;
        cursorY = location[1] + target.getHeight() * 0.5f;
        playerCursorActive = true; playerCursor.setVisibility(View.VISIBLE); positionPlayerCursor();
        Toast.makeText(this, "Player-Cursor: Steuerkreuz bewegen, OK klicken, Zurück beenden", Toast.LENGTH_LONG).show();
    }

    private void hidePlayerCursor() {
        playerCursorActive = false;
        if (playerCursor != null) playerCursor.setVisibility(View.GONE);
        webView.requestFocus();
    }

    private void positionPlayerCursor() {
        playerCursor.setX(cursorX - playerCursor.getLayoutParams().width / 2f);
        playerCursor.setY(cursorY - playerCursor.getLayoutParams().height / 2f);
        playerCursor.bringToFront();
    }

    private void clickPlayerCursor() {
        WebView target = playerWebView != null ? playerWebView : webView;
        int[] location = new int[2]; target.getLocationInWindow(location);
        float x = cursorX - location[0], y = cursorY - location[1];
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0);
        target.dispatchTouchEvent(down); target.dispatchTouchEvent(up);
        down.recycle(); up.recycle();
    }

    @SuppressLint("SetJavaScriptEnabled") private void openNativePlayer(String url) {
        if (url == null || url.trim().isEmpty()) return;
        closeNativePlayer();
        playerWebView = new WebView(this);
        playerWebView.setBackgroundColor(Color.BLACK);
        playerWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings ps = playerWebView.getSettings();
        ps.setJavaScriptEnabled(true); ps.setDomStorageEnabled(true); ps.setDatabaseEnabled(true);
        ps.setMediaPlaybackRequiresUserGesture(false); ps.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        ps.setJavaScriptCanOpenWindowsAutomatically(false); ps.setSupportMultipleWindows(false);
        ps.setUserAgentString(webView.getSettings().getUserAgentString());
        CookieManager.getInstance().setAcceptThirdPartyCookies(playerWebView, true);
        playerWebView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                view.loadUrl(request.getUrl().toString()); return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String requestUrl = request.getUrl().toString();
                if (isDirectMediaUrl(requestUrl)) {
                    launchNativeStreamOnce(requestUrl, request.getRequestHeaders().get("Referer"));
                    return emptyMediaResponse(requestUrl);
                }
                return AdBlocker.shouldBlock(requestUrl, blockerEnabled) ? AdBlocker.emptyResponse() : super.shouldInterceptRequest(view, request);
            }
        });
        playerWebView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, android.os.Message resultMsg) { return false; }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view; customViewCallback = callback; root.addView(view, new FrameLayout.LayoutParams(-1,-1)); playerWebView.setVisibility(View.GONE);
            }
            @Override public void onHideCustomView() { hideCustomView(); }
        });
        root.addView(playerWebView, root.indexOfChild(playerCursor), new FrameLayout.LayoutParams(-1,-1));
        playerWebView.loadUrl(url);
        showPlayerCursor();
    }

    private void closeNativePlayer() {
        if (playerWebView == null) return;
        root.removeView(playerWebView); playerWebView.stopLoading(); playerWebView.destroy(); playerWebView = null;
        hidePlayerCursor(); webView.setVisibility(View.VISIBLE);
    }


    private boolean isDirectMediaUrl(String url) {
        if (url == null) return false;
        String clean = url.toLowerCase();
        int query = clean.indexOf('?');
        if (query >= 0) clean = clean.substring(0, query);
        return clean.endsWith(".m3u8") || clean.endsWith(".mp4") || clean.endsWith(".m4v")
            || clean.endsWith(".webm") || clean.contains("/hls/") || clean.contains("manifest.m3u8");
    }

    private WebResourceResponse emptyMediaResponse(String url) {
        String mime = url != null && url.toLowerCase().contains(".m3u8")
            ? "application/vnd.apple.mpegurl" : "video/mp4";
        return new WebResourceResponse(mime, "UTF-8", new java.io.ByteArrayInputStream(new byte[0]));
    }

    private void launchNativeStreamOnce(String url, String referer) {
        if (url == null || url.equals(nativeStreamUrl) || url.equals(pendingNativeStreamUrl)) return;
        pendingNativeStreamUrl = url;
        runOnUiThread(() -> {
            if (!url.equals(pendingNativeStreamUrl) || nativeVideo != null) return;
            pauseAllWebPlayback();
            startNativeVideo(url, referer);
            pendingNativeStreamUrl = null;
        });
    }

    private void pauseAllWebPlayback() {
        String pauseJs = "(function(){document.querySelectorAll('video,audio').forEach(function(m){try{m.pause();m.muted=true}catch(e){}})})()";
        if (webView != null) {
            webView.evaluateJavascript(pauseJs, null);
            webView.onPause();
            webView.pauseTimers();
            webView.setVisibility(View.INVISIBLE);
        }
        if (playerWebView != null) {
            playerWebView.evaluateJavascript(pauseJs, null);
            playerWebView.onPause();
            playerWebView.setVisibility(View.INVISIBLE);
        }
    }

    private void resumeWebAfterNativePlayer() {
        if (webView != null) {
            webView.resumeTimers();
            webView.onResume();
            webView.setVisibility(View.VISIBLE);
            webView.requestFocus();
        }
        if (playerWebView != null) {
            playerWebView.onResume();
            playerWebView.setVisibility(View.VISIBLE);
        }
    }

    private void startNativeVideo(String url, String referer) {
        if (url == null || url.equals(nativeStreamUrl) || nativeVideo != null) return;
        nativeStreamUrl = url;
        hidePlayerCursor();
        webView.evaluateJavascript("document.querySelectorAll('iframe').forEach(function(f){if(/redirect|voe|dood|filemoon|vidmoly/i.test(f.src||''))f.src='about:blank'})", null);
        nativeVideo = new SurfaceView(this);
        nativeVideo.setBackgroundColor(Color.BLACK);
        nativeVideo.setZOrderOnTop(true);
        nativeVideo.getHolder().setFormat(PixelFormat.OPAQUE);
        root.addView(nativeVideo, new FrameLayout.LayoutParams(-1, -1));
        nativeVideo.bringToFront();
        nativeVideo.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {
                try {
                    nativePlayer = new MediaPlayer();
                    nativePlayer.setDisplay(holder);
                    nativePlayer.setScreenOnWhilePlaying(true);
                    nativePlayer.setOnPreparedListener(mp -> { mp.start(); Toast.makeText(MainActivity.this, "Nativer Player: OK Pause · ◀/▶ 10 Sekunden · Zurück schließen", Toast.LENGTH_LONG).show(); });
                    nativePlayer.setOnErrorListener((mp, what, extra) -> { Toast.makeText(MainActivity.this, "Stream konnte nativ nicht geöffnet werden ("+what+"/"+extra+")", Toast.LENGTH_LONG).show(); return true; });
                    HashMap<String,String> headers = new HashMap<>();
                    headers.put("User-Agent", webView.getSettings().getUserAgentString());
                    if (referer != null && !referer.isEmpty()) headers.put("Referer", referer);
                    String cookie = CookieManager.getInstance().getCookie(url);
                    if (cookie != null) headers.put("Cookie", cookie);
                    nativePlayer.setDataSource(MainActivity.this, Uri.parse(url), headers);
                    nativePlayer.prepareAsync();
                } catch (Exception error) { Toast.makeText(MainActivity.this, "Nativer Player Fehler: "+error.getMessage(), Toast.LENGTH_LONG).show(); closeNativeVideo(); }
            }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
            @Override public void surfaceDestroyed(SurfaceHolder holder) { }
        });
    }

    private void closeNativeVideo() {
        if (nativePlayer != null) { try { nativePlayer.stop(); } catch (Exception ignored) { } nativePlayer.release(); nativePlayer = null; }
        if (nativeVideo != null) { root.removeView(nativeVideo); nativeVideo = null; }
        nativeStreamUrl = null;
        pendingNativeStreamUrl = null;
        resumeWebAfterNativePlayer();
    }

    private void toggleBlocker() {
        blockerEnabled = !blockerEnabled;
        shieldStatus.setText(blockerEnabled ? "SCHUTZ AN" : "SCHUTZ AUS");
        shieldStatus.setTextColor(blockerEnabled ? Color.rgb(242,22,75) : Color.LTGRAY);
        Toast.makeText(this, blockerEnabled ? "Werbe- und Trackingschutz aktiviert" : "Schutz deaktiviert", Toast.LENGTH_SHORT).show();
        webView.reload();
    }

    private void injectOverlayBlocker(WebView view) {
        String js = "(function(){function clean(){document.querySelectorAll('iframe').forEach(function(f){var c=getComputedStyle(f),z=Number.parseInt(c.zIndex)||0;if((c.position==='fixed'&&z>99999)||/^container-[a-f0-9]{16,}$/i.test(f.id||'')||/^container-[a-f0-9]{16,}$/i.test(f.className||''))f.remove()});document.querySelectorAll('body *').forEach(function(e){var t=(e.innerText||'').toLowerCase();if((t.indexOf('plötzlich insider')>=0||t.indexOf('lese die erste folge')>=0||t.indexOf('#handjob')>=0)&&e.children.length<20){var n=e;while(n.parentElement&&n.parentElement!==document.body){var s=getComputedStyle(n);if(s.position==='fixed'&&((Number.parseInt(s.zIndex)||0)>999)){n.remove();break}n=n.parentElement}}})}clean();if(!window.__neoOverlayGuard){window.__neoOverlayGuard=1;new MutationObserver(clean).observe(document.documentElement,{childList:true,subtree:true})}})()";
        view.evaluateJavascript(js, null);
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView); customView = null;
        if (playerWebView != null) playerWebView.setVisibility(View.VISIBLE); else webView.setVisibility(View.VISIBLE);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
    }

    private boolean isAllowedMainFrame(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase();
        return host.equals("186.2.175.5") || host.equals("serienstream.to") || host.endsWith(".serienstream.to")
            || host.equals("aniflix.uno") || host.endsWith(".aniflix.uno")
            || host.equals("youtube.com") || host.endsWith(".youtube.com")
            || host.equals("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com")
            || host.equals("youtu.be");
    }

    @Override public void onBackPressed() {
        if (nativeVideo != null) closeNativeVideo(); else if (customView != null) hideCustomView(); else if (playerWebView != null) closeNativePlayer(); else if (playerCursorActive) hidePlayerCursor(); else if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (nativeVideo != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && nativePlayer != null) {
                int key = event.getKeyCode();
                if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    if (nativePlayer.isPlaying()) nativePlayer.pause(); else nativePlayer.start();
                } else if (key == KeyEvent.KEYCODE_DPAD_LEFT) nativePlayer.seekTo(Math.max(0, nativePlayer.getCurrentPosition()-10000));
                else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) nativePlayer.seekTo(nativePlayer.getCurrentPosition()+10000);
                else if (key == KeyEvent.KEYCODE_BACK) closeNativeVideo();
                else return super.dispatchKeyEvent(event);
            }
            return true;
        }
        if (playerCursorActive) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int step = dp(event.getRepeatCount() > 5 ? 26 : 9), key = event.getKeyCode();
                WebView target = playerWebView != null ? playerWebView : webView;
                int[] location = new int[2]; target.getLocationInWindow(location);
                float minX = location[0] + dp(10), maxX = location[0] + target.getWidth() - dp(10);
                float minY = location[1] + dp(10), maxY = location[1] + target.getHeight() - dp(10);
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) cursorX = Math.max(minX, cursorX - step);
                else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) cursorX = Math.min(maxX, cursorX + step);
                else if (key == KeyEvent.KEYCODE_DPAD_UP) cursorY = Math.max(minY, cursorY - step);
                else if (key == KeyEvent.KEYCODE_DPAD_DOWN) cursorY = Math.min(maxY, cursorY + step);
                else if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) clickPlayerCursor();
                else if (key == KeyEvent.KEYCODE_BACK) { if (playerWebView != null) closeNativePlayer(); else hidePlayerCursor(); }
                else return super.dispatchKeyEvent(event);
                positionPlayerCursor();
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && navigationBar != null) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && navigationBar.getVisibility() != View.VISIBLE && !webView.canScrollVertically(1)) {
                navigationBar.setVisibility(View.VISIBLE);
                if (navigationBar.getChildCount() > 0) navigationBar.getChildAt(0).requestFocus();
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && navigationBar.getVisibility() == View.VISIBLE) {
                navigationBar.setVisibility(View.GONE);
                webView.requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onSaveInstanceState(Bundle out) { webView.saveState(out); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() { closeNativeVideo(); webView.destroy(); super.onDestroy(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
