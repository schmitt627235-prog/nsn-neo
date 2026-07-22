package de.nsn.neo.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import de.nsn.neo.BuildConfig;
import de.nsn.neo.NsnApplication;
import de.nsn.neo.R;
import de.nsn.neo.model.Episode;
import de.nsn.neo.model.MediaItem;
import de.nsn.neo.model.HosterOption;
import de.nsn.neo.model.SourceId;
import de.nsn.neo.model.ContentType;
import de.nsn.neo.model.ResolvedStream;
import de.nsn.neo.player.Media3PlaybackEngine;
import de.nsn.neo.player.PlaybackEngine;
import de.nsn.neo.source.Callback;
import de.nsn.neo.source.SourceProvider;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/** Native detail page: source HTML is parsed but never shown. */
public final class DetailActivity extends Activity {
    private LinearLayout content;
    private SourceProvider provider;
    private String contentId;
    private LinearLayout selection;
    private MediaItem mediaItem;
    private LinearLayout episodeArea;
    private ScrollView detailScroll;
    private PlaybackEngine trailerPlayer;
    private final android.os.Handler trailerHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        NsnViews.applyMobileImmersiveBars(this);
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        SourceId source = SourceId.valueOf(getIntent().getStringExtra("source"));
        provider = ((NsnApplication) getApplication()).sources().get(source);
        contentId = getIntent().getStringExtra("content");
        detailScroll = new ScrollView(this); content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL); content.setBackgroundColor(Color.BLACK);
        TextView loading = NsnViews.text(this, "Details werden geladen …", BuildConfig.IS_TV ? 24 : 18, Color.WHITE);
        loading.setPadding(NsnViews.dp(this, 30), NsnViews.dp(this, 50), 0, 0); content.addView(loading);
        detailScroll.addView(content); setContentView(detailScroll);
        provider.details(contentId, new Callback<MediaItem>() {
            @Override public void onSuccess(MediaItem value) { runOnUiThread(() -> showDetails(value)); }
            @Override public void onError(Throwable error) { runOnUiThread(() -> loading.setText("Details konnten nicht geladen werden")); }
        });
    }

    private void showDetails(MediaItem item) {
        mediaItem = item;
        content.removeAllViews();
        FrameLayout hero = new FrameLayout(this); hero.setBackgroundColor(Color.rgb(10,10,10));
        FrameLayout trailerLayer = new FrameLayout(this); hero.addView(trailerLayer, new FrameLayout.LayoutParams(-1,-1));
        ImageView backdrop = new ImageView(this); backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP); backdrop.setAlpha(.35f);
        PosterLoader.load(backdrop, item.backdropUrl); hero.addView(backdrop, new FrameLayout.LayoutParams(-1,-1));
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.BOTTOM);
        row.setPadding(NsnViews.dp(this, BuildConfig.IS_TV ? 55 : 20), NsnViews.dp(this,20), NsnViews.dp(this,20), NsnViews.dp(this,35));
        ImageView poster = new ImageView(this); poster.setScaleType(ImageView.ScaleType.CENTER_CROP); PosterLoader.load(poster,item.posterUrl);
        row.addView(poster,new LinearLayout.LayoutParams(NsnViews.dp(this,BuildConfig.IS_TV?190:115),NsnViews.dp(this,BuildConfig.IS_TV?285:173)));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.setPadding(NsnViews.dp(this,20),0,0,0);
        TextView title=NsnViews.text(this,item.title,BuildConfig.IS_TV?34:28,Color.WHITE); title.setTypeface(null,android.graphics.Typeface.BOLD); title.setMaxLines(2); copy.addView(title);
        String cleanDescription=item.description.replaceAll("(?i)\\s*mehr anzeigen\\s*$","").trim();
        TextView description=NsnViews.text(this,cleanDescription,BuildConfig.IS_TV?18:15,getColor(R.color.nsn_muted)); description.setMaxLines(BuildConfig.IS_TV?4:8); copy.addView(description);
        TextView favorite=option(((NsnApplication)getApplication()).library().isFavorite(item.source,item.id)?"✓ In meiner Liste":"＋ Meine Liste");
        favorite.setOnClickListener(v->{boolean added=((NsnApplication)getApplication()).library().toggleFavorite(item);((TextView)v).setText(added?"✓ In meiner Liste":"＋ Meine Liste");});copy.addView(favorite);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1)); hero.addView(row,new FrameLayout.LayoutParams(-1,-1));
        content.addView(hero,new LinearLayout.LayoutParams(-1,NsnViews.dp(this,BuildConfig.IS_TV?500:410)));
        if (item.trailerUrl != null && item.source != SourceId.FILMPALAST) {
            trailerHandler.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                trailerPlayer = new Media3PlaybackEngine(this);
                trailerPlayer.attach(trailerLayer, false);
                trailerPlayer.setVolume(0f); trailerPlayer.setRepeat(true);
                trailerPlayer.prepare(new ResolvedStream(item.trailerUrl, null, java.util.Collections.emptyMap(), null), 0, true);
                backdrop.animate().alpha(.18f).setDuration(500).start();
            }, 1500);
        }
        TextView episodeHeading=NsnViews.heading(this,"Staffeln & Episoden",BuildConfig.IS_TV);
        provider.episodes(contentId,new Callback<List<Episode>>() {
            @Override public void onSuccess(List<Episode> episodes){runOnUiThread(()->showSeasonPicker(episodes));}
            @Override public void onError(Throwable error){runOnUiThread(()->episodeHeading.setText("Keine Episoden verfügbar"));}
        });
    }

    private void showSeasonPicker(List<Episode> episodes) {
        if(episodes.isEmpty())return;
        if(mediaItem!=null&&mediaItem.type==ContentType.MOVIE){
            selection=new LinearLayout(this);selection.setOrientation(LinearLayout.VERTICAL);content.addView(selection);
            selection.addView(NsnViews.heading(this,"Film abspielen",BuildConfig.IS_TV));
            showLanguages(episodes.get(0));return;
        }
        Set<Integer> seasons=new LinkedHashSet<>();for(Episode episode:episodes)seasons.add(Math.max(1,episode.season));
        content.addView(NsnViews.heading(this,"Staffeln",BuildConfig.IS_TV));
        HorizontalScrollView seasonScroll=new HorizontalScrollView(this);seasonScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout seasonRail=new LinearLayout(this);seasonRail.setOrientation(LinearLayout.HORIZONTAL);
        for(Integer season:seasons){TextView chip=option("Staffel "+season);chip.setTag(season);chip.setOnClickListener(v->showSeason(episodes,(Integer)v.getTag()));seasonRail.addView(chip);}
        seasonScroll.addView(seasonRail);content.addView(seasonScroll);
        content.addView(NsnViews.heading(this,"Episoden",BuildConfig.IS_TV));episodeArea=new LinearLayout(this);episodeArea.setOrientation(LinearLayout.VERTICAL);content.addView(episodeArea);
        selection=new LinearLayout(this);selection.setOrientation(LinearLayout.VERTICAL);content.addView(selection);
        int firstSeason=seasons.iterator().next();showSeason(episodes,firstSeason);
        for(Episode episode:episodes)if(Math.max(1,episode.season)==firstSeason){showLanguages(episode);break;}
    }

    private void showSeason(List<Episode> episodes,int season){
        episodeArea.removeAllViews();HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout rail=new LinearLayout(this);rail.setOrientation(LinearLayout.HORIZONTAL);
        for(Episode episode:episodes)if(Math.max(1,episode.season)==season){TextView chip=option("Episode "+episode.number);chip.setTag(episode);chip.setOnClickListener(v->showLanguages((Episode)v.getTag()));rail.addView(chip);}
        scroll.addView(rail);episodeArea.addView(scroll);
    }

    private void showEpisodesLegacy(List<Episode> episodes) {
        HorizontalScrollView scroll=new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout rail=new LinearLayout(this); rail.setOrientation(LinearLayout.HORIZONTAL);
        for(Episode episode:episodes){
            TextView chip=NsnViews.text(this,"S"+episode.season+" · E"+episode.number,BuildConfig.IS_TV?20:16,Color.WHITE);
            chip.setFocusable(BuildConfig.IS_TV); chip.setClickable(true); chip.setTag(episode);
            chip.setOnClickListener(v->showLanguages((Episode)v.getTag()));
            chip.setPadding(NsnViews.dp(this,18),NsnViews.dp(this,14),NsnViews.dp(this,18),NsnViews.dp(this,14)); rail.addView(chip);
        }
        scroll.addView(rail); content.addView(scroll);
    }

    private void showLanguages(Episode episode){
        selection.removeAllViews(); selection.addView(NsnViews.heading(this,"Sprache",BuildConfig.IS_TV));
        provider.languages(contentId,episode.id,new Callback<List<String>>(){
            @Override public void onSuccess(List<String> values){runOnUiThread(()->{
                LinearLayout row=new LinearLayout(DetailActivity.this); row.setOrientation(LinearLayout.HORIZONTAL);
                for(String value:values){TextView chip=option(value);chip.setOnClickListener(v->showHosters(episode,value));row.addView(chip);} selection.addView(row);
                if(!values.isEmpty()){View first=row.getChildAt(0);focusAndReveal(first);if(values.size()==1)showHosters(episode,values.get(0));}
            });}
            @Override public void onError(Throwable error){runOnUiThread(()->showHosters(episode,null));}
        });
    }

    private void showHosters(Episode episode,String language){
        provider.hosters(contentId,episode.id,language,new Callback<List<HosterOption>>(){
            @Override public void onSuccess(List<HosterOption> values){runOnUiThread(()->{
                // Keep the selected episode and language together; AniWorld's hosters
                // must follow immediately, without a second full-screen section/spacer.
                if(selection==null)return;
                selection.setMinimumHeight(0); selection.setPadding(0,0,0,0);
                while(selection.getChildCount()>2)selection.removeViewAt(2);
                selection.addView(NsnViews.heading(DetailActivity.this,"Hoster ausw\u00e4hlen und abspielen",BuildConfig.IS_TV)); LinearLayout row=new LinearLayout(DetailActivity.this);row.setOrientation(LinearLayout.HORIZONTAL);
                row.setMinimumHeight(0); row.setPadding(0,0,0,0);
                for(HosterOption value:values){TextView chip=option(value.name);chip.setOnClickListener(v->{
                    android.content.Intent intent=new android.content.Intent(DetailActivity.this,PlayerActivity.class);
                    intent.putExtra("hoster",value.url); intent.putExtra("source",provider.id().name());
                    intent.putExtra("hosterName",value.name); intent.putExtra("language",language);
                    intent.putExtra("content",contentId); intent.putExtra("episode",episode.id);
                    intent.putExtra("title",mediaItem==null?"":mediaItem.title);
                    intent.putExtra("subtitle","S"+episode.season+" · E"+episode.number+(episode.title==null?"":" · "+episode.title));
                    intent.putExtra("poster",mediaItem==null?null:mediaItem.posterUrl); startActivity(intent);
                });row.addView(chip);}selection.addView(row);selection.requestLayout();if(row.getChildCount()>0)focusAndReveal(row.getChildAt(0));
            });}
            @Override public void onError(Throwable error){ }
        });
    }
    private TextView option(String label){
        TextView chip=NsnViews.text(this,label,BuildConfig.IS_TV?20:16,Color.WHITE);chip.setFocusable(BuildConfig.IS_TV);chip.setClickable(true);
        chip.setPadding(NsnViews.dp(this,18),NsnViews.dp(this,14),NsnViews.dp(this,18),NsnViews.dp(this,14));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);params.setMargins(NsnViews.dp(this,8),NsnViews.dp(this,8),NsnViews.dp(this,8),NsnViews.dp(this,8));chip.setLayoutParams(params);
        if(BuildConfig.IS_TV)chip.setOnFocusChangeListener((v,focused)->{GradientDrawable bg=new GradientDrawable();bg.setColor(focused?Color.rgb(28,12,14):Color.rgb(20,20,20));bg.setStroke(NsnViews.dp(this,focused?4:1),focused?getColor(R.color.nsn_red):Color.DKGRAY);bg.setCornerRadius(NsnViews.dp(this,10));v.setBackground(bg);v.animate().scaleX(focused?1.08f:1f).scaleY(focused?1.08f:1f).translationZ(focused?NsnViews.dp(this,12):0).setDuration(120).start();});
        return chip;
    }
    private void focusAndReveal(View view){
        if(!BuildConfig.IS_TV)return;
        view.post(()->{view.requestFocus();detailScroll.smoothScrollTo(0,Math.max(0,view.getTop()+selection.getTop()-NsnViews.dp(this,180)));});
    }

    @Override protected void onPause(){super.onPause();if(trailerPlayer!=null)trailerPlayer.pause();}
    @Override protected void onDestroy(){trailerHandler.removeCallbacksAndMessages(null);if(trailerPlayer!=null){trailerPlayer.release();trailerPlayer=null;}super.onDestroy();}
}
