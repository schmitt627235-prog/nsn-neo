package de.nsn.neo.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import de.nsn.neo.R;
import de.nsn.neo.NsnApplication;
import de.nsn.neo.model.MediaItem;
import de.nsn.neo.model.SourceId;
import de.nsn.neo.data.PlaybackRecord;
import de.nsn.neo.source.Callback;
import de.nsn.neo.source.GenreLink;
import de.nsn.neo.source.HomeSection;
import de.nsn.neo.source.SourceProvider;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class HomeActivityBase extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout navigation;
    private View splash;
    private ImageView brandMark;
    private ScrollView homeScroll;
    private LinearLayout homeContent;
    private LinearLayout genreChipsHost;
    private LinearLayout genreRowsHost;
    private View lastContentFocus;
    private String pendingFocusKey;
    private String selectedGenre = "Alle";
    private GenreLink selectedGenreLink;
    private int homeGeneration;
    private final Map<String, LinkedHashMap<String, MediaItem>> genreCatalog = new LinkedHashMap<>();
    private final Map<SourceId,List<GenreLink>> sourceGenres = new LinkedHashMap<>();
    private final Map<String,List<MediaItem>> genreResults = new HashMap<>();
    private final Set<String> loadingGenres = new HashSet<>();
    private boolean homeVisible;
    private String activeSection="Start";
    private int filmpalastPage=1;
    protected abstract boolean isTv();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        NsnViews.applyMobileImmersiveBars(this);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        showSplashThenHome();
    }

    private void showSplashThenHome() {
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        ImageView banner = new ImageView(this); banner.setImageResource(R.drawable.nsn_home_background);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP); banner.setBackgroundColor(Color.BLACK);
        root.addView(banner, new FrameLayout.LayoutParams(-1, -1));
        TextView loading = NsnViews.text(this, "Inhalte werden vorbereitet …", isTv() ? 20 : 16, Color.WHITE);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        loadingParams.bottomMargin = NsnViews.dp(this, 34); root.addView(loading, loadingParams);
        splash = root; setContentView(root);
        handler.postDelayed(this::showHome, 650);
    }

    private void showHome() {
        final int generation = ++homeGeneration;
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        ImageView fixedBackground = new ImageView(this); fixedBackground.setImageResource(R.drawable.nsn_home_background);
        // Keep the brand texture as a restrained backdrop.  Content cards and
        // headings must remain the visual focus, especially on mobile screens.
        fixedBackground.setScaleType(ImageView.ScaleType.CENTER_CROP); fixedBackground.setAlpha(isTv() ? .12f : .08f);
        root.addView(fixedBackground, new FrameLayout.LayoutParams(-1,-1));
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        navigation = createNavigation();
        ScrollView vertical = new ScrollView(this); vertical.setFillViewport(true); homeScroll = vertical;
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setClipChildren(false);
        homeContent = content;
        content.setPadding(0,NsnViews.dp(this,isTv()?58:52),0,NsnViews.dp(this,40));
        // Phase 6: keep the Netflix-like hero as the first content block.  It
        // uses the existing NSN artwork only; source data and navigation stay
        // unchanged.  Rows below remain horizontally scrollable.
        content.addView(createHero(), new LinearLayout.LayoutParams(-1, NsnViews.dp(this, isTv() ? 360 : 270)));
        if("Start".equals(activeSection)){
            addContinueWatching(content);
            addFavorites(content);
        }
        if("Genres".equals(activeSection)) addGenreBrowser(content);
        if(!"Genres".equals(activeSection)) {
            for(SourceProvider provider:((NsnApplication)getApplication()).sources().all())
                if(matchesSection(provider.id()))addSourceRail(content,provider,generation);
        }
        if("Start".equals(activeSection)){
            // The requested order is source rows (ending with films), real
            // source genres, then the combined calendar.
            addGenreChips(content);
            loadGenreMetadata(generation);
            addCombinedCalendar(content);
        } else if("Filme".equals(activeSection)){
            addGenreChips(content);
            loadGenreMetadata(generation);
        } else if("Genres".equals(activeSection)){
            loadGenreMetadata(generation);
        }
        vertical.addView(content, new ScrollView.LayoutParams(-1, -2));
        page.addView(vertical, new LinearLayout.LayoutParams(-1, 0, 1)); root.addView(page, new FrameLayout.LayoutParams(-1,-1));
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(-1, NsnViews.dp(this, isTv() ? 72 : 60), Gravity.TOP);
        root.addView(navigation, navParams);
        brandMark = new ImageView(this); brandMark.setImageResource(R.drawable.nsn_wordmark);
        brandMark.setScaleType(ImageView.ScaleType.CENTER_INSIDE); brandMark.setAlpha(.82f);
        FrameLayout.LayoutParams wordmarkParams = new FrameLayout.LayoutParams(
                NsnViews.dp(this,isTv()?300:190),NsnViews.dp(this,isTv()?58:42),Gravity.TOP|Gravity.END);
        wordmarkParams.topMargin=NsnViews.dp(this,isTv()?8:64);wordmarkParams.rightMargin=NsnViews.dp(this,isTv()?26:12);
        root.addView(brandMark,wordmarkParams);
        TextView quickSearch=NsnViews.text(this,"⌕  Suche",isTv()?19:16,Color.WHITE);quickSearch.setGravity(Gravity.CENTER);
        quickSearch.setFocusable(false);quickSearch.setClickable(true);quickSearch.setPadding(NsnViews.dp(this,16),0,NsnViews.dp(this,16),0);
        quickSearch.setBackgroundColor(Color.argb(210,18,18,18));quickSearch.setOnClickListener(v->startActivity(NavigationRoutes.search(this)));
        quickSearch.setOnFocusChangeListener((v,f)->{v.setBackgroundColor(f?getColor(R.color.nsn_red):Color.argb(210,18,18,18));v.animate().scaleX(f?1.06f:1f).scaleY(f?1.06f:1f).setDuration(110).start();});
        FrameLayout.LayoutParams searchParams=new FrameLayout.LayoutParams(NsnViews.dp(this,isTv()?150:112),NsnViews.dp(this,isTv()?48:42),Gravity.TOP|Gravity.START);
        searchParams.leftMargin=NsnViews.dp(this,isTv()?20:10);searchParams.topMargin=NsnViews.dp(this,isTv()?12:64);
        if(!isTv())root.addView(quickSearch,searchParams);
        if (isTv()) {
            navigation.setVisibility(View.VISIBLE);
            navigation.setAlpha(1f);
        }
        else vertical.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            boolean hide = y > NsnViews.dp(this, 48);
            if (!hide) navigation.setVisibility(View.VISIBLE);
            navigation.animate().alpha(hide ? 0f : 1f).setDuration(140).withEndAction(() -> { if (hide) navigation.setVisibility(View.GONE); }).start();
        });
        setContentView(root); root.setAlpha(0f); root.animate().alpha(1f).setDuration(240).start();
        if (splash != null) splash = null;
        homeVisible=true;
        if(isTv())handler.post(()->{
            if(pendingFocusKey==null)focusActiveNavigation();
            else restoreVisibleFocus();
        });
    }

    @Override protected void onPause(){
        if(isTv()) {
            View focused=getCurrentFocus();
            if(focused!=null&&!isDescendant(navigation,focused)){
                lastContentFocus=focused;
                pendingFocusKey=focusKey(focused);
            }
        }
        super.onPause();
    }

    @Override protected void onResume(){super.onResume();NsnViews.applyMobileImmersiveBars(this);if(homeVisible)showHome();}

    private LinearLayout createNavigation() {
        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(NsnViews.dp(this, 20), 0, NsnViews.dp(this, 20), 0);
        bar.setBackgroundColor(Color.argb(188, 5, 5, 5));
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.nsn_logo); logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        bar.addView(logo, new LinearLayout.LayoutParams(NsnViews.dp(this, isTv() ? 92 : 64), -1));
        String[] labels = {"Suche", "Start", "Anime", "Serien", "Filme", "Genres"};
        for (String label : labels) {
            TextView item = NsnViews.text(this, label, isTv() ? 17 : 14, Color.WHITE);
            item.setGravity(Gravity.CENTER); item.setFocusable(isTv()); item.setClickable(true);
            item.setSingleLine(true);
            if(label.equals(activeSection))item.setTextColor(getColor(R.color.nsn_red));
            int horizontalPadding = isTv() ? 18 : 10;
            item.setPadding(NsnViews.dp(this, horizontalPadding), 0, NsnViews.dp(this, horizontalPadding), 0);
            if ("Suche".equals(label)) item.setOnClickListener(v -> {
                pendingFocusKey="nav|Suche";
                startActivity(NavigationRoutes.search(this));
            });
            if ("Start".equals(label)||"Anime".equals(label)||"Serien".equals(label)||"Filme".equals(label)||"Genres".equals(label)) item.setOnClickListener(v->{activeSection=label;pendingFocusKey="nav|"+label;showHome();});
            if (isTv()) item.setOnFocusChangeListener((v, focused) -> {
                v.setBackgroundColor(focused ? getColor(R.color.nsn_red) : Color.TRANSPARENT);
                v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(110).start();
            });
            if(isTv())item.setOnKeyListener((v,key,event)->{
                if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_DOWN){
                    restoreContentFocus();
                    return true;
                }
                return false;
            });
            item.setTag("nav|"+label);
            bar.addView(item, new LinearLayout.LayoutParams(-2, -1));
        }
        return bar;
    }

    private boolean matchesSection(SourceId id){
        if("Start".equals(activeSection))return true;
        if("Anime".equals(activeSection))return id==SourceId.ANIWORLD;
        if("Serien".equals(activeSection))return id==SourceId.SERIENSTREAMS;
        if("Filme".equals(activeSection))return id==SourceId.FILMPALAST;
        return false;
    }

    private View createHero() {
        FrameLayout hero = new FrameLayout(this); hero.setBackgroundColor(Color.rgb(15, 7, 8));
        ImageView art = new ImageView(this); art.setImageResource(R.drawable.nsn_banner); art.setScaleType(ImageView.ScaleType.CENTER_CROP); art.setAlpha(0.55f);
        hero.addView(art, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.setGravity(Gravity.BOTTOM);
        copy.setPadding(NsnViews.dp(this, isTv() ? 54 : 22), 0, NsnViews.dp(this, 22), NsnViews.dp(this, isTv() ? 24 : 22));
        TextView eyebrow = NsnViews.text(this, "NEXT-STREAMING-NEO", isTv() ? 17 : 14, getColor(R.color.nsn_red));
        TextView title = NsnViews.text(this, "Anime. Serien. Filme.", isTv() ? 34 : 29, Color.WHITE); title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView description = NsnViews.text(this, "Eine native Oberfläche für deine verbundenen Medienquellen.", isTv() ? 20 : 16, getColor(R.color.nsn_muted));
        description.setTextSize(isTv() ? 17 : 15);
        copy.addView(eyebrow); copy.addView(title); copy.addView(description); hero.addView(copy, new FrameLayout.LayoutParams(-1, -1));
        return hero;
    }

    private void addRail(LinearLayout target, String title, String[] cards) {
        target.addView(NsnViews.heading(this, title, isTv()));
        HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false); scroll.setClipToPadding(false); scroll.setFillViewport(false);
        LinearLayout rail = NsnViews.rail(this, cards, isTv()); scroll.addView(rail, new HorizontalScrollView.LayoutParams(-2, -2));
        target.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addContinueWatching(LinearLayout target){
        List<PlaybackRecord> records=((NsnApplication)getApplication()).library().playback();
        if(records.isEmpty())return;
        // A series is represented by one visible card.  Keep all episode
        // records in the store, but show the episode with the newest playback
        // timestamp so resume always continues the most recently watched one.
        java.util.LinkedHashMap<String,PlaybackRecord> latestBySeries=new java.util.LinkedHashMap<>();
        for(PlaybackRecord record:records){
            String seriesKey=record.source.name()+"|"+(record.contentId==null?"":record.contentId);
            PlaybackRecord current=latestBySeries.get(seriesKey);
            if(current==null||record.updatedAt>current.updatedAt)latestBySeries.put(seriesKey,record);
        }
        records=new ArrayList<>(latestBySeries.values());
        records.sort(Comparator.comparingLong((PlaybackRecord r)->r.updatedAt).reversed());
        target.addView(NsnViews.heading(this,"Weiterschauen",isTv())); HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);scroll.setClipChildren(false);scroll.setClipToPadding(false);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setClipChildren(false);
        for(PlaybackRecord record:records){
            View card=NsnViews.playbackCard(this,record,isTv(),v->resume((PlaybackRecord)v.getTag()));
            if(isTv())card.setOnKeyListener((v,key,event)->{
                if(event.getAction()==KeyEvent.ACTION_DOWN&&(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_BUTTON_MODE)){
                    showContinueMenu((PlaybackRecord)v.getTag(),v,row);return true;
                }
                return false;
            });
            else card.setOnLongClickListener(v->{showContinueMenu((PlaybackRecord)v.getTag(),v,row);return true;});
            row.addView(card);
            maybeRestoreFocus(card);
        }
        scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));target.addView(scroll);
    }

    private void showContinueMenu(PlaybackRecord record,View card,LinearLayout row){
        new android.app.AlertDialog.Builder(this)
                .setTitle(record.title==null?"Weiterschauen":record.title)
                .setItems(new String[]{"Aus Weiterschauen entfernen"},(dialog,which)->{
                    ((NsnApplication)getApplication()).library().removePlayback(record);
                    row.removeView(card);
                    if(row.getChildCount()==0)showHome();
                })
                .setNegativeButton("Abbrechen",null)
                .show();
    }

    private void addFavorites(LinearLayout target){
        List<MediaItem> items=((NsnApplication)getApplication()).library().favorites();if(items.isEmpty())return;
        target.addView(NsnViews.heading(this,"Meine Liste",isTv()));HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipChildren(false);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setClipChildren(false);
        for(MediaItem item:items){
            View card=NsnViews.card(this,item,isTv(),v->openDetails((MediaItem)v.getTag()));
            row.addView(card);
            maybeRestoreFocus(card);
        }
        scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));target.addView(scroll);
    }

    private void resume(PlaybackRecord record){
        startActivity(NavigationRoutes.detail(this, record.source, record.contentId));
    }

    private void addSourceRail(LinearLayout target, SourceProvider provider, int generation) {
        LinearLayout section = new LinearLayout(this); section.setOrientation(LinearLayout.VERTICAL);
        TextView heading = NsnViews.heading(this, provider.id().name(), isTv()); section.addView(heading);
        TextView state = NsnViews.text(this, "Wird geladen …", isTv() ? 17 : 14, getColor(R.color.nsn_muted));
        state.setPadding(NsnViews.dp(this, 20), NsnViews.dp(this, 8), 0, NsnViews.dp(this, 20)); section.addView(state);
        target.addView(section, new LinearLayout.LayoutParams(-1, -2));
        loadSourceSection(section,provider,generation);
    }

    private void loadSourceSection(LinearLayout section,SourceProvider provider,int generation){
        section.removeAllViews();TextView state=NsnViews.text(this,"Wird geladen …",isTv()?17:14,getColor(R.color.nsn_muted));state.setPadding(NsnViews.dp(this,20),NsnViews.dp(this,8),0,NsnViews.dp(this,20));section.addView(state);
        Callback<List<HomeSection>> homeCallback=new Callback<List<HomeSection>>() {
            @Override public void onSuccess(List<HomeSection> sections) { runOnUiThread(() -> {
                if(generation!=homeGeneration)return;
                collectGenres(sections);
                section.removeAllViews();
                if (sections.isEmpty()) { section.addView(NsnViews.heading(HomeActivityBase.this, provider.id().name(), isTv())); return; }
                for (HomeSection home : sections) {
                    section.addView(NsnViews.heading(HomeActivityBase.this, home.title, isTv()));
                    HorizontalScrollView scroll = new HorizontalScrollView(HomeActivityBase.this);
                    scroll.setHorizontalScrollBarEnabled(false); scroll.setClipChildren(false); scroll.setClipToPadding(false);
                    LinearLayout row = new LinearLayout(HomeActivityBase.this); row.setOrientation(LinearLayout.HORIZONTAL); row.setClipChildren(false);
                    if(provider.id()==SourceId.FILMPALAST&&filmpalastPage>1)row.addView(pageCard("← Vorherige Seite",-1,section,provider));
                    for (MediaItem item : home.items) {
                        View card=NsnViews.card(HomeActivityBase.this, item, isTv(), v -> openDetails((MediaItem) v.getTag()));
                        row.addView(card);
                        maybeRestoreFocus(card);
                    }
                    if(provider.id()==SourceId.FILMPALAST)row.addView(pageCard("Nächste Seite →",1,section,provider));
                    scroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -2)); section.addView(scroll);
                }
            }); }
            @Override public void onError(Throwable error) { runOnUiThread(() -> state.setText("Quelle derzeit nicht erreichbar")); }
        };
        if(provider.id()==SourceId.FILMPALAST)provider.homePage(filmpalastPage,homeCallback);else provider.home(homeCallback);
    }

    private View pageCard(String label,int delta,LinearLayout section,SourceProvider provider){
        LinearLayout card=NsnViews.card(this,label,isTv(),true,false);card.setOnClickListener(v->{filmpalastPage=Math.max(1,filmpalastPage+delta);loadSourceSection(section,provider,homeGeneration);});return card;
    }

    private void collectGenres(List<HomeSection> sections){
        for(HomeSection section:sections)for(MediaItem item:section.items){
            for(String raw:item.genres){
                String genre=raw==null?"":raw.trim();
                if(genre.isEmpty())continue;
                genreCatalog.computeIfAbsent(genre,key->new LinkedHashMap<>())
                        .putIfAbsent(item.source.name()+"|"+item.id,item);
            }
        }
        renderGenreChips();
        renderStartGenreRows();
        renderGenreResults();
    }

    private void loadGenreMetadata(int generation){
        for(SourceProvider provider:((NsnApplication)getApplication()).sources().all()){
            provider.genres(new Callback<List<GenreLink>>(){
                @Override public void onSuccess(List<GenreLink> links){
                    runOnUiThread(()->{
                        if(generation!=homeGeneration)return;
                        sourceGenres.put(provider.id(),new ArrayList<>(links));
                        renderGenreChips();
                        renderGenreResults();
                    });
                }
                @Override public void onError(Throwable error){ }
            });
        }
    }

    private void addGenreChips(LinearLayout target){
        target.addView(NsnViews.heading(this,"Genres entdecken",isTv()));
        genreChipsHost=new LinearLayout(this);
        genreChipsHost.setOrientation(LinearLayout.VERTICAL);
        target.addView(genreChipsHost,new LinearLayout.LayoutParams(-1,-2));
        renderGenreChips();
    }

    private void addGenreBrowser(LinearLayout target){
        target.addView(NsnViews.heading(this,"Genres",isTv()));
        genreChipsHost=new LinearLayout(this);
        genreChipsHost.setOrientation(LinearLayout.VERTICAL);
        target.addView(genreChipsHost,new LinearLayout.LayoutParams(-1,-2));
        genreRowsHost=new LinearLayout(this);
        genreRowsHost.setOrientation(LinearLayout.VERTICAL);
        target.addView(genreRowsHost,new LinearLayout.LayoutParams(-1,-2));
        renderGenreChips();
        renderGenreResults();
    }

    private void renderGenreChips(){
        if(genreChipsHost==null)return;
        genreChipsHost.removeAllViews();
        for(SourceProvider provider:((NsnApplication)getApplication()).sources().all()){
            if("Filme".equals(activeSection)&&provider.id()!=SourceId.FILMPALAST)continue;
            List<GenreLink> links=new ArrayList<>(sourceGenres.getOrDefault(provider.id(),List.of()));
            if(links.isEmpty())continue;
            links.sort(Comparator.comparing(link->link.name,String.CASE_INSENSITIVE_ORDER));
            TextView sourceHeading=NsnViews.text(this,sourceLabel(provider.id()),isTv()?18:15,Color.WHITE);
            sourceHeading.setTypeface(null,android.graphics.Typeface.BOLD);
            sourceHeading.setPadding(NsnViews.dp(this,20),NsnViews.dp(this,8),0,0);
            genreChipsHost.addView(sourceHeading);
            HorizontalScrollView scroll=new HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            for(GenreLink link:links)row.addView(genreChip(link));
            scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));
            genreChipsHost.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        }
    }

    private View genreChip(GenreLink link){
        TextView chip=NsnViews.text(this,link.name,isTv()?16:14,Color.WHITE);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setFocusable(isTv());
        chip.setClickable(true);
        chip.setTag("genre|"+link.source+"|"+link.url);
        chip.setPadding(NsnViews.dp(this,isTv()?20:14),NsnViews.dp(this,10),
                NsnViews.dp(this,isTv()?20:14),NsnViews.dp(this,10));
        boolean selected=selectedGenreLink!=null&&link.url.equals(selectedGenreLink.url);
        chip.setBackgroundColor(selected?getColor(R.color.nsn_red):Color.argb(220,28,28,28));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);
        params.setMargins(NsnViews.dp(this,10),NsnViews.dp(this,6),NsnViews.dp(this,4),NsnViews.dp(this,12));
        chip.setLayoutParams(params);
        chip.setOnFocusChangeListener((v,focused)->{
            boolean active=selectedGenreLink!=null&&link.url.equals(selectedGenreLink.url);
            v.setBackgroundColor(focused||active?getColor(R.color.nsn_red):Color.argb(220,28,28,28));
            v.animate().scaleX(focused?1.06f:1f).scaleY(focused?1.06f:1f).setDuration(100).start();
        });
        chip.setOnClickListener(v->{
            selectedGenre=link.name;
            selectedGenreLink=link;
            if(!"Genres".equals(activeSection)){
                activeSection="Genres";
                pendingFocusKey="genre|"+link.source+"|"+link.url;
                showHome();
            }else{
                renderGenreChips();
                renderGenreResults();
            }
        });
        maybeRestoreFocus(chip);
        return chip;
    }

    private void renderStartGenreRows(){
        if(genreRowsHost==null||!"Start".equals(activeSection))return;
        genreRowsHost.removeAllViews();
        List<String> genres=new ArrayList<>(genreCatalog.keySet());
        genres.sort(String.CASE_INSENSITIVE_ORDER);
        for(String genre:genres)addGenreRail(genreRowsHost,genre,new ArrayList<>(genreCatalog.get(genre).values()));
    }

    private void renderGenreResults(){
        if(genreRowsHost==null||!"Genres".equals(activeSection))return;
        genreRowsHost.removeAllViews();
        if(selectedGenreLink==null)return;
        List<MediaItem> items=genreResults.get(selectedGenreLink.url);
        if(items!=null){addGenreRail(genreRowsHost,sourceLabel(selectedGenreLink.source)+" · "+selectedGenre,items);return;}
        if(!loadingGenres.add(selectedGenreLink.url))return;
        TextView loading=NsnViews.text(this,"Genre wird geladen …",isTv()?17:14,getColor(R.color.nsn_muted));
        loading.setPadding(NsnViews.dp(this,20),NsnViews.dp(this,12),0,NsnViews.dp(this,20));
        genreRowsHost.addView(loading);
        for(SourceProvider provider:((NsnApplication)getApplication()).sources().all()){
            if(provider.id()!=selectedGenreLink.source)continue;
            GenreLink request=selectedGenreLink;
            provider.genreItems(request.url,new Callback<List<MediaItem>>(){
                @Override public void onSuccess(List<MediaItem> result){runOnUiThread(()->{
                    loadingGenres.remove(request.url);
                    genreResults.put(request.url,new ArrayList<>(result));
                    if(selectedGenreLink!=null&&request.url.equals(selectedGenreLink.url))renderGenreResults();
                });}
                @Override public void onError(Throwable error){runOnUiThread(()->{
                    loadingGenres.remove(request.url);
                    if(selectedGenreLink!=null&&request.url.equals(selectedGenreLink.url)){
                        genreRowsHost.removeAllViews();
                        genreRowsHost.addView(NsnViews.text(HomeActivityBase.this,"Genre derzeit nicht erreichbar",isTv()?17:14,getColor(R.color.nsn_muted)));
                    }
                });}
            });
            break;
        }
    }

    private static String sourceLabel(SourceId source){
        if(source==SourceId.ANIWORLD)return "AniWorld";
        if(source==SourceId.SERIENSTREAMS)return "SerienStreams";
        return "Filmpalast";
    }

    private void addGenreRail(LinearLayout target,String genre,List<MediaItem> items){
        if(items.isEmpty())return;
        target.addView(NsnViews.heading(this,genre,isTv()));
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        for(MediaItem item:items){
            View card=NsnViews.card(this,item,isTv(),v->openDetails((MediaItem)v.getTag()));
            row.addView(card);
            maybeRestoreFocus(card);
        }
        scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));
        target.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
    }

    private void addCombinedCalendar(LinearLayout target){
        LinearLayout section=new LinearLayout(this);section.setOrientation(LinearLayout.VERTICAL);section.addView(NsnViews.heading(this,"Anime- & Serienkalender",isTv()));
        TextView loading=NsnViews.text(this,"Kalender werden zusammengeführt …",isTv()?17:14,getColor(R.color.nsn_muted));loading.setPadding(NsnViews.dp(this,20),8,0,24);section.addView(loading);target.addView(section);
        List<MediaItem> combined=java.util.Collections.synchronizedList(new ArrayList<>());AtomicInteger pending=new AtomicInteger(2);
        for(SourceProvider provider:((NsnApplication)getApplication()).sources().all())if(provider.id()==SourceId.ANIWORLD||provider.id()==SourceId.SERIENSTREAMS)provider.calendar(new Callback<List<MediaItem>>(){
            @Override public void onSuccess(List<MediaItem> items){combined.addAll(items);finish();}
            @Override public void onError(Throwable error){finish();}
            private void finish(){if(pending.decrementAndGet()!=0)return;runOnUiThread(()->renderCalendar(section,combined));}
        });
    }

    private void renderCalendar(LinearLayout section,List<MediaItem> items){
        section.removeViews(1,section.getChildCount()-1);java.util.LinkedHashMap<String,MediaItem> unique=new java.util.LinkedHashMap<>();
        for(MediaItem item:items){String cleanTitle=item.title.replaceAll("(?i)\\s*[~·-]?\\s*\\d{1,2}:\\d{2}(?:\\s*Uhr)?\\s*$","").trim();MediaItem clean=new MediaItem(item.id,item.source,item.type,cleanTitle,item.description,item.posterUrl,item.backdropUrl,item.detailUrl,item.genres,item.year,item.rating,item.trailerUrl);unique.putIfAbsent(item.source+"|"+item.description+"|"+cleanTitle,clean);}
        items=new ArrayList<>(unique.values());items.sort(Comparator.comparing(i->i.description==null?"":i.description));
        LinearLayout columns=new LinearLayout(this);columns.setOrientation(LinearLayout.HORIZONTAL);columns.setWeightSum(3f);
        LinearLayout early=calendarColumn("Früh · 00:00–08:00");LinearLayout noon=calendarColumn("Mittag · 08:00–16:00");LinearLayout evening=calendarColumn("Abend · 16:00–00:00");
        columns.addView(early,new LinearLayout.LayoutParams(0,-2,1f));columns.addView(noon,new LinearLayout.LayoutParams(0,-2,1f));columns.addView(evening,new LinearLayout.LayoutParams(0,-2,1f));
        for(MediaItem item:items){int hour=calendarHour(item.description);addCalendarEntry(hour<8?early:hour<16?noon:evening,item);}section.addView(columns,new LinearLayout.LayoutParams(-1,-2));
    }

    private LinearLayout calendarColumn(String title){LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);column.setPadding(8,4,8,18);TextView header=NsnViews.text(this,title,isTv()?18:14,Color.WHITE);header.setTypeface(null,android.graphics.Typeface.BOLD);header.setPadding(10,10,10,12);column.addView(header);return column;}
    private int calendarHour(String value){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d{1,2}):\\d{2}").matcher(value==null?"":value);return m.find()?Integer.parseInt(m.group(1)):0;}
    private void addCalendarEntry(LinearLayout column,MediaItem item){TextView entry=NsnViews.text(this,"",isTv()?13:12,Color.WHITE);String value=item.description+"\n"+item.title;android.text.SpannableString styled=new android.text.SpannableString(value);if(value.startsWith("✓"))styled.setSpan(new android.text.style.ForegroundColorSpan(Color.rgb(75,210,60)),0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);entry.setText(styled);entry.setMaxLines(3);entry.setFocusable(isTv());entry.setClickable(true);entry.setTag(item);entry.setPadding(12,8,12,8);entry.setBackgroundColor(Color.argb(210,20,20,20));entry.setOnClickListener(v->openDetails((MediaItem)v.getTag()));entry.setOnFocusChangeListener((v,f)->v.setBackgroundColor(f?getColor(R.color.nsn_red):Color.argb(210,20,20,20)));column.addView(entry,new LinearLayout.LayoutParams(-1,-2));}

    private void openDetails(MediaItem item) {
        startActivity(NavigationRoutes.detail(this, item));
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(isTv()&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP){
            View current=getCurrentFocus();
            if(current!=null&&!isDescendant(navigation,current)){
                View next=current.focusSearch(View.FOCUS_UP);
                if(next==null||next==current||isDescendant(navigation,next)
                        ||(homeScroll!=null&&homeScroll.getScrollY()<=NsnViews.dp(this,16))){
                    lastContentFocus=current;
                    pendingFocusKey=focusKey(current);
                    focusActiveNavigation();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void focusActiveNavigation(){
        if(navigation==null)return;
        String wanted="nav|"+activeSection;
        for(int i=0;i<navigation.getChildCount();i++){
            View child=navigation.getChildAt(i);
            if(wanted.equals(child.getTag())){
                child.requestFocus();
                return;
            }
        }
        if(navigation.getChildCount()>1)navigation.getChildAt(1).requestFocus();
    }

    private void restoreContentFocus(){
        if(lastContentFocus!=null&&lastContentFocus.isAttachedToWindow()
                &&lastContentFocus.getVisibility()==View.VISIBLE&&lastContentFocus.isFocusable()){
            lastContentFocus.requestFocus();
            return;
        }
        if(restoreVisibleFocus())return;
        View first=findFirstFocusable(homeContent);
        if(first!=null)first.requestFocus();
    }

    private boolean restoreVisibleFocus(){
        if(pendingFocusKey==null)return false;
        View match=findFocusKey(getWindow().getDecorView(),pendingFocusKey);
        if(match==null)return false;
        match.requestFocus();
        lastContentFocus=isDescendant(navigation,match)?lastContentFocus:match;
        pendingFocusKey=null;
        return true;
    }

    private void maybeRestoreFocus(View view){
        if(!isTv()||pendingFocusKey==null)return;
        if(pendingFocusKey.equals(focusKey(view))){
            handler.post(()->{
                if(view.isAttachedToWindow()){
                    view.requestFocus();
                    lastContentFocus=view;
                    pendingFocusKey=null;
                }
            });
        }
    }

    private String focusKey(View view){
        if(view==null)return null;
        Object tag=view.getTag();
        if(tag instanceof String)return (String)tag;
        if(tag instanceof MediaItem){
            MediaItem item=(MediaItem)tag;
            return "media|"+item.source.name()+"|"+item.id;
        }
        if(tag instanceof PlaybackRecord){
            PlaybackRecord record=(PlaybackRecord)tag;
            return "playback|"+record.source.name()+"|"+record.contentId+"|"+record.episodeId;
        }
        return null;
    }

    private View findFocusKey(View view,String key){
        if(view==null||key==null)return null;
        if(key.equals(focusKey(view))&&view.isFocusable()&&view.getVisibility()==View.VISIBLE)return view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                View result=findFocusKey(group.getChildAt(i),key);
                if(result!=null)return result;
            }
        }
        return null;
    }

    private View findFirstFocusable(View view){
        if(view==null||view.getVisibility()!=View.VISIBLE)return null;
        if(view.isFocusable()&&view.isClickable())return view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                View result=findFirstFocusable(group.getChildAt(i));
                if(result!=null)return result;
            }
        }
        return null;
    }

    private boolean isDescendant(ViewGroup parent,View child){
        if(parent==null||child==null)return false;
        View current=child;
        while(current!=null){
            if(current==parent)return true;
            android.view.ViewParent next=current.getParent();
            current=next instanceof View?(View)next:null;
        }
        return false;
    }

    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
