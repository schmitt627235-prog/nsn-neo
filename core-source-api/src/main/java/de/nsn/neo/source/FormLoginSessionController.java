package de.nsn.neo.source;

import de.nsn.neo.session.SourceSession;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Native form login with source-owned cookies and optional CSRF fields. */
public final class FormLoginSessionController implements SessionController {
    private final SourceSession session; private final HttpTransport transport; private final String origin;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    public FormLoginSessionController(SourceSession session,HttpTransport transport,String origin){this.session=session;this.transport=transport;this.origin=origin;}
    @Override public boolean requiresLogin(){return true;}
    @Override public boolean isLoggedIn(){return session.isLoggedIn();}
    @Override public void login(String email,char[] password,Callback<Boolean> callback){
        worker.execute(()->{try{
            String loginUrl=origin+"login"; Document page=Jsoup.parse(transport.get(loginUrl),loginUrl); Map<String,String> fields=new LinkedHashMap<>();
            for(Element hidden:page.select("form[action*=login] input[type=hidden][name]"))fields.put(hidden.attr("name"),hidden.attr("value"));
            fields.put("email",email);fields.put("password",new String(password));fields.put("autoLogin","on");
            String result=transport.postForm(loginUrl,fields);boolean ok=result.toLowerCase().contains("logout")||result.toLowerCase().contains("abmelden");session.setLoggedIn(ok);
            java.util.Arrays.fill(password,'\0');callback.onSuccess(ok);
        }catch(Throwable error){java.util.Arrays.fill(password,'\0');callback.onError(error);}});
    }
    @Override public void logout(Callback<Boolean> callback){worker.execute(()->{try{transport.get(origin+"logout");session.clear();callback.onSuccess(true);}catch(Throwable error){session.clear();callback.onError(error);}});}
}
