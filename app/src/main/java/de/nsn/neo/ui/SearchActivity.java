package de.nsn.neo.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import de.nsn.neo.BuildConfig;
import de.nsn.neo.NsnApplication;
import de.nsn.neo.model.MediaItem;
import de.nsn.neo.model.SourceId;
import de.nsn.neo.source.Callback;
import de.nsn.neo.source.SourceProvider;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class SearchActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper()); private final AtomicInteger generation=new AtomicInteger();
    private LinearLayout results;
    @Override protected void onCreate(Bundle state){super.onCreate(state);NsnViews.applyMobileImmersiveBars(this);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.BLACK);page.setPadding(NsnViews.dp(this,BuildConfig.IS_TV?55:18),NsnViews.dp(this,BuildConfig.IS_TV?32:18),NsnViews.dp(this,18),0);
        EditText query=new EditText(this);query.setHint("Anime, Serie oder Film suchen");query.setHintTextColor(Color.GRAY);query.setTextColor(Color.WHITE);query.setSingleLine(true);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);query.setTextSize(BuildConfig.IS_TV?24:18);query.setFocusable(true);page.addView(query,new LinearLayout.LayoutParams(-1,NsnViews.dp(this,BuildConfig.IS_TV?68:56)));
        ScrollView scroll=new ScrollView(this);results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);scroll.addView(results);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!BuildConfig.IS_TV)schedule(s.toString());}public void afterTextChanged(Editable e){}});
        query.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH||(event!=null&&event.getKeyCode()==android.view.KeyEvent.KEYCODE_ENTER)){int token=generation.incrementAndGet();handler.removeCallbacksAndMessages(null);search(v.getText().toString().trim(),token);return true;}return false;});
        query.requestFocus();query.postDelayed(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(query,InputMethodManager.SHOW_IMPLICIT),BuildConfig.IS_TV?320:180);
    }
    private void schedule(String value){int token=generation.incrementAndGet();handler.removeCallbacksAndMessages(null);handler.postDelayed(()->search(value.trim(),token),280);}
    private void search(String query,int token){results.removeAllViews();if(query.length()<2)return;TextView loading=NsnViews.heading(this,"Suche läuft …",BuildConfig.IS_TV);results.addView(loading);java.util.concurrent.atomic.AtomicInteger pending=new java.util.concurrent.atomic.AtomicInteger(((NsnApplication)getApplication()).sources().all().size());java.util.concurrent.atomic.AtomicInteger found=new java.util.concurrent.atomic.AtomicInteger();for(SourceProvider provider:((NsnApplication)getApplication()).sources().all())provider.search(query,new Callback<List<MediaItem>>(){
        public void onSuccess(List<MediaItem> items){android.util.Log.i("NSN_SEARCH",provider.id()+" token="+token+" hits="+items.size());runOnUiThread(()->{if(token!=generation.get())return;if(loading.getParent()!=null)results.removeView(loading);if(!items.isEmpty()){found.addAndGet(items.size());TextView heading=NsnViews.heading(SearchActivity.this,sourceLabel(provider.id()),BuildConfig.IS_TV);results.addView(heading);HorizontalScrollView scroller=new HorizontalScrollView(SearchActivity.this);scroller.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(SearchActivity.this);row.setOrientation(LinearLayout.HORIZONTAL);for(MediaItem item:items)row.addView(NsnViews.card(SearchActivity.this,item,BuildConfig.IS_TV,v->open((MediaItem)v.getTag())));scroller.addView(row);results.addView(scroller);}if(pending.decrementAndGet()==0&&found.get()==0)results.addView(NsnViews.heading(SearchActivity.this,"Keine Treffer gefunden",BuildConfig.IS_TV));});}
        public void onError(Throwable error){android.util.Log.e("NSN_SEARCH",provider.id()+" token="+token+" failed",error);runOnUiThread(()->{if(token!=generation.get())return;if(pending.decrementAndGet()==0&&found.get()==0){if(loading.getParent()!=null)results.removeView(loading);results.addView(NsnViews.heading(SearchActivity.this,"Keine Treffer gefunden",BuildConfig.IS_TV));}});}
    });}
    private static String sourceLabel(SourceId source){if(source==SourceId.FILMPALAST)return "Filme";if(source==SourceId.SERIENSTREAMS)return "Serien";return "Anime";}
    private void open(MediaItem item){startActivity(NavigationRoutes.detail(this,item));}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
