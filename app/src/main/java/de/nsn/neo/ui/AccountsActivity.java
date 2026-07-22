package de.nsn.neo.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import de.nsn.neo.BuildConfig;
import de.nsn.neo.NsnApplication;
import de.nsn.neo.R;
import de.nsn.neo.model.SourceId;
import de.nsn.neo.source.Callback;
import de.nsn.neo.source.SourceProvider;

/** Two independent accounts; credentials are never persisted. */
public final class AccountsActivity extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state); getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        ScrollView scroll=new ScrollView(this); LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(Color.BLACK);
        page.setPadding(NsnViews.dp(this,BuildConfig.IS_TV?70:22),NsnViews.dp(this,35),NsnViews.dp(this,BuildConfig.IS_TV?70:22),NsnViews.dp(this,35));
        page.addView(NsnViews.heading(this,"Konten",BuildConfig.IS_TV)); addLogin(page,SourceId.ANIWORLD,"AniWorld"); addLogin(page,SourceId.SERIENSTREAMS,"SerienStreams");
        TextView noLogin=NsnViews.text(this,"Filmpalast benötigt kein Konto.",BuildConfig.IS_TV?19:15,getColor(R.color.nsn_muted)); noLogin.setPadding(0,NsnViews.dp(this,28),0,0); page.addView(noLogin);
        scroll.addView(page); setContentView(scroll);
    }
    private void addLogin(LinearLayout page,SourceId id,String label){
        SourceProvider provider=((NsnApplication)getApplication()).sources().get(id); page.addView(NsnViews.heading(this,label,BuildConfig.IS_TV));
        EditText email=input("E-Mail-Adresse",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password=input("Passwort",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); page.addView(email); page.addView(password);
        Button login=new Button(this); login.setText(provider.session().isLoggedIn()?"Abmelden":"Anmelden"); login.setFocusable(true); page.addView(login,new LinearLayout.LayoutParams(-1,NsnViews.dp(this,BuildConfig.IS_TV?64:52)));
        TextView status=NsnViews.text(this,"",BuildConfig.IS_TV?18:14,getColor(R.color.nsn_muted)); page.addView(status);
        login.setOnClickListener(v->{
            if(provider.session().isLoggedIn()){provider.session().logout(new Callback<Boolean>(){public void onSuccess(Boolean ok){runOnUiThread(()->{login.setText("Anmelden");status.setText("Abgemeldet");});}public void onError(Throwable e){runOnUiThread(()->status.setText("Abmeldung lokal abgeschlossen"));}});return;}
            String mail=email.getText().toString().trim(); char[] secret=new char[password.length()]; password.getText().getChars(0,password.length(),secret,0); status.setText("Anmeldung läuft …");
            provider.session().login(mail,secret,new Callback<Boolean>(){public void onSuccess(Boolean ok){runOnUiThread(()->{password.setText("");status.setText(ok?"Erfolgreich angemeldet":"Anmeldung abgelehnt");if(ok)login.setText("Abmelden");});}public void onError(Throwable e){runOnUiThread(()->{password.setText("");status.setText("Anmeldung derzeit nicht möglich");});}});
        });
    }
    private EditText input(String hint,int type){EditText field=new EditText(this);field.setHint(hint);field.setHintTextColor(Color.GRAY);field.setTextColor(Color.WHITE);field.setInputType(type);field.setSingleLine(true);field.setFocusable(true);field.setPadding(NsnViews.dp(this,16),NsnViews.dp(this,12),NsnViews.dp(this,16),NsnViews.dp(this,12));return field;}
}
