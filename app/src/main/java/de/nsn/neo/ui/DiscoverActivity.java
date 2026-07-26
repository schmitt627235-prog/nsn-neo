package de.nsn.neo.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import de.nsn.neo.BuildConfig;
import de.nsn.neo.NsnApplication;
import de.nsn.neo.R;
import de.nsn.neo.model.MediaItem;
import de.nsn.neo.model.SourceId;
import de.nsn.neo.source.Callback;
import de.nsn.neo.source.GenreLink;
import de.nsn.neo.source.SourceProvider;
import java.util.List;

/**
 * Native discovery generator. It deliberately uses the existing providers
 * instead of displaying either source website or its hoster UI.
 */
public final class DiscoverActivity extends Activity {
    private LinearLayout content;
    private LinearLayout resultHost;
    private SourceId selectedSource=SourceId.ANIWORLD;
    private GenreLink selectedGenre;
    private boolean episodeMode;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        NsnViews.applyMobileImmersiveBars(this);
        render();
    }

    private void render(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(NsnViews.dp(this,BuildConfig.IS_TV?44:16),NsnViews.dp(this,24),
                NsnViews.dp(this,BuildConfig.IS_TV?44:16),NsnViews.dp(this,40));
        content.setBackgroundColor(Color.BLACK);
        TextView back=NsnViews.action(this,"‹ Zurück",false,BuildConfig.IS_TV);
        back.setOnClickListener(v->finish());
        content.addView(back,new LinearLayout.LayoutParams(-2,-2));
        content.addView(NsnViews.heading(this,"Inhalte entdecken",BuildConfig.IS_TV));
        TextView description=NsnViews.text(this,
                "Lass dir aus den echten AniWorld- oder SerienStreams-Inhalten etwas Passendes vorschlagen.",
                BuildConfig.IS_TV?17:14,getColor(R.color.nsn_muted));
        description.setPadding(0,0,0,NsnViews.dp(this,18));
        content.addView(description);
        addSourceChoices();
        addModeChoices();
        loadGenres();
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        setContentView(scroll);
    }

    private void addSourceChoices(){
        content.addView(NsnViews.heading(this,"Bereich",BuildConfig.IS_TV));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(choice("Anime",selectedSource==SourceId.ANIWORLD,v->{selectedSource=SourceId.ANIWORLD;selectedGenre=null;render();}));
        row.addView(choice("Serien",selectedSource==SourceId.SERIENSTREAMS,v->{selectedSource=SourceId.SERIENSTREAMS;selectedGenre=null;render();}));
        content.addView(row);
    }

    private void addModeChoices(){
        content.addView(NsnViews.heading(this,"Vorschlag",BuildConfig.IS_TV));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(choice("Zufälliger Titel",!episodeMode,v->{episodeMode=false;render();}));
        row.addView(choice("Zufällige Episode",episodeMode,v->{episodeMode=true;render();}));
        content.addView(row);
    }

    private TextView choice(String label,boolean active,View.OnClickListener listener){
        TextView view=NsnViews.action(this,label,active,BuildConfig.IS_TV);
        view.setOnClickListener(listener);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);
        params.setMargins(0,0,NsnViews.dp(this,10),NsnViews.dp(this,10));
        view.setLayoutParams(params);
        return view;
    }

    private void loadGenres(){
        SourceProvider provider=provider();
        if(provider==null)return;
        TextView state=NsnViews.text(this,"Genres werden geladen …",BuildConfig.IS_TV?17:14,getColor(R.color.nsn_muted));
        content.addView(state);
        provider.genres(new Callback<List<GenreLink>>(){
            @Override public void onSuccess(List<GenreLink> genres){runOnUiThread(()->showGenres(state,genres));}
            @Override public void onError(Throwable error){runOnUiThread(()->showGenres(state,List.of()));}
        });
    }

    private void showGenres(TextView state,List<GenreLink> genres){
        content.removeView(state);
        content.addView(NsnViews.heading(this,"Genre (optional)",BuildConfig.IS_TV));
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(choice("Alle",selectedGenre==null,v->{selectedGenre=null;render();}));
        for(GenreLink genre:genres)
            row.addView(choice(genre.name,selectedGenre!=null&&genre.url.equals(selectedGenre.url),v->{selectedGenre=genre;render();}));
        scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));
        content.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        TextView generate=NsnViews.action(this,"▶ Vorschlag generieren",true,BuildConfig.IS_TV);
        generate.setOnClickListener(v->generate(generate));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);
        params.topMargin=NsnViews.dp(this,18);
        content.addView(generate,params);
        resultHost=new LinearLayout(this);
        resultHost.setOrientation(LinearLayout.VERTICAL);
        content.addView(resultHost,new LinearLayout.LayoutParams(-1,-2));
    }

    private void generate(TextView button){
        button.setEnabled(false);
        button.setText("Vorschlag wird ermittelt …");
        SourceProvider provider=provider();
        Callback<List<MediaItem>> callback=new Callback<List<MediaItem>>(){
            @Override public void onSuccess(List<MediaItem> items){runOnUiThread(()->showResult(button,items));}
            @Override public void onError(Throwable error){runOnUiThread(()->showResult(button,List.of()));}
        };
        provider.discover(selectedGenre==null?null:selectedGenre.url,20,callback);
    }

    private void showResult(TextView button,List<MediaItem> items){
        button.setEnabled(true);
        button.setText("↻ Neu würfeln");
        resultHost.removeAllViews();
        if(items.isEmpty()){
            resultHost.addView(NsnViews.text(this,"Für diese Auswahl wurde kein Inhalt gefunden.",
                    BuildConfig.IS_TV?17:14,getColor(R.color.nsn_muted)));
            return;
        }
        resultHost.addView(NsnViews.heading(this,
                episodeMode?"20 zufällige Episoden":"20 zufällige Titel",BuildConfig.IS_TV));
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for(MediaItem item:items){
            View card=NsnViews.card(this,item,BuildConfig.IS_TV,v->
                    startActivity(NavigationRoutes.detail(this,item.source,item.id)));
            LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(
                    NsnViews.dp(this,BuildConfig.IS_TV?260:176),-2);
            cardParams.setMargins(0,0,NsnViews.dp(this,12),0);
            row.addView(card,cardParams);
        }
        scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-2));
        resultHost.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
    }

    private SourceProvider provider(){
        for(SourceProvider source:((NsnApplication)getApplication()).sources().all())
            if(source.id()==selectedSource)return source;
        return null;
    }
}
