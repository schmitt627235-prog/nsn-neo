package de.nsn.neo.source.filmpalast;

import de.nsn.neo.model.ContentType;
import de.nsn.neo.model.SourceId;
import de.nsn.neo.source.SourceMetadata;
import de.nsn.neo.source.HtmlSourceProvider;
import de.nsn.neo.source.Callback;
import de.nsn.neo.source.HomeSection;
import de.nsn.neo.model.MediaItem;
import de.nsn.neo.model.Episode;
import de.nsn.neo.model.HosterOption;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class FilmpalastSource extends HtmlSourceProvider {
    public static final SourceMetadata METADATA = new SourceMetadata(
            SourceId.FILMPALAST, "Filmpalast", "https://filmpalast.to/", ContentType.MOVIE, false);
    public static final String MOVIES_PATH = "movies/new";
    public FilmpalastSource() { super(SourceId.FILMPALAST, METADATA.baseUrl, ContentType.MOVIE, false); }
    @Override protected String homeUrl() { return METADATA.baseUrl; }
    @Override protected String contentPathSelector() {
        return "article.liste > a[href*=/stream/]:has(img[src*=/files/movies/])";
    }
    @Override protected boolean accepts(Element link, String title, String url) {
        return url.contains("/stream/");
    }
    @Override public void home(Callback<List<HomeSection>> callback){homePage(1,callback);}
    @Override public void homePage(int page,Callback<List<HomeSection>> callback){async(callback,()->{
        List<HomeSection> sections=new ArrayList<>();
        String url=METADATA.baseUrl+(page>1?"page/"+page:"");
        List<MediaItem> newest=cards(load(url),60);if(!newest.isEmpty())sections.add(new HomeSection("movies-new","Neu bei Filmpalast · Seite "+page,newest));
        return sections;
    });}
    @Override public void search(String query,Callback<List<MediaItem>> callback){async(callback,()->{
        String encoded=java.net.URLEncoder.encode(query,"UTF-8").replace("+","%20");
        Document doc=load(METADATA.baseUrl+"search/title/"+encoded);
        Map<String,MediaItem> unique=new LinkedHashMap<>();
        for(Element link:doc.select("a[href*=/stream/]")){
            String url=link.absUrl("href");if(url.isBlank())url=absolute(link.attr("href"));String title=cleanText(titleOf(link));
            if(title.isBlank()){Element img=findCardImage(link);if(img!=null)title=cleanText(img.attr("alt").replaceFirst("(?i)^stream\\s+",""));}
            if(title.isBlank()||!matchesQuery(title,query))continue;Element image=findCardImage(link);String poster=imageUrl(image);
            unique.putIfAbsent(url,new MediaItem(url,id(),ContentType.MOVIE,title,"",poster,poster,url,List.of(),null,null));if(unique.size()>=100)break;
        }return new ArrayList<>(unique.values());
    });}
    private static boolean matchesQuery(String title,String query){
        String hay=org.jsoup.Jsoup.parse(title).text().toLowerCase(java.util.Locale.ROOT);String needle=query.toLowerCase(java.util.Locale.ROOT).trim();
        if(needle.isBlank())return false;for(String word:needle.split("\\s+"))if(!hay.contains(word))return false;return true;
    }

    /** A movie is represented as one playable unit so the shared detail UI can
     * continue directly with language/host selection without fake seasons. */
    @Override public void episodes(String contentId, Callback<List<Episode>> callback) {
        callback.onSuccess(List.of(new Episode(absolute(contentId), 1, 1, "Film abspielen", "", null, 0)));
    }

    @Override public void languages(String contentId, String episodeId, Callback<List<String>> callback) {
        callback.onSuccess(List.of("Deutsch"));
    }

    @Override public void hosters(String contentId, String episodeId, String language, Callback<List<HosterOption>> callback) {
        async(callback, () -> {
            Document doc=load(absolute(contentId));List<HosterOption> result=new ArrayList<>();
            for(Element link:doc.select("a[href*='voe.'], a[href*='dood.'], a[href*='streamtape.'], a[href*='vidoza.'], a[href*='vidmoly.'], a[href*='filemoon.']")){
                String url=link.absUrl("href");if(url.isBlank())url=absolute(link.attr("href"));
                String host;
                try{host=java.net.URI.create(url).getHost();}catch(Exception ignored){host=link.text();}
                if(host==null||host.isBlank())host="Stream";
                boolean exists=false;for(HosterOption item:result)if(item.url.equals(url)){exists=true;break;}
                if(!exists)result.add(new HosterOption(url,host.replace("www.",""),url,language));
            }
            return result;
        });
    }
}
