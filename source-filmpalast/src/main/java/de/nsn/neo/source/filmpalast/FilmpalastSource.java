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
        List<MediaItem> newest=cards(load(url),60);if(!newest.isEmpty())sections.add(new HomeSection("movies-new","Neueste Filme · Seite "+page,newest));
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
            if(poster==null||!poster.toLowerCase(java.util.Locale.ROOT).contains("/files/movies/"))poster=moviePoster(url);
            unique.putIfAbsent(url,new MediaItem(url,id(),ContentType.MOVIE,title,"",poster,poster,url,List.of(),null,null));if(unique.size()>=100)break;
        }return new ArrayList<>(unique.values());
    });}
    private String moviePoster(String url){
        try{
            Document detail=load(url);
            Element image=detail.selectFirst("img[src*=/files/movies/],img[data-src*=/files/movies/],img[data-original*=/files/movies/],source[srcset*=/files/movies/]");
            String poster=imageUrl(image);
            if(poster!=null)return poster;
            Element meta=detail.selectFirst("meta[property=og:image],meta[name=twitter:image]");
            if(meta!=null&&!meta.attr("content").isBlank())return absolute(meta.attr("content"));
        }catch(Exception ignored){}
        return null;
    }
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
            Document doc=load(absolute(contentId));Map<String,HosterOption> result=new LinkedHashMap<>();
            for(Element link:doc.select("a[href], [data-url], [data-href], [data-link], [data-src], [data-play-url], [onclick]")){
                String url=firstNonBlank(link.absUrl("href"),link.attr("data-url"),link.attr("data-href"),
                        link.attr("data-link"),link.attr("data-src"),link.attr("data-play-url"));
                if(url.isBlank()&&link.hasAttr("onclick")){
                    java.util.regex.Matcher match=java.util.regex.Pattern
                            .compile("(?i)(https?://[^'\\\"\\s)]+|/[^'\\\"\\s)]+(?:redirect|stream)[^'\\\"\\s)]*)")
                            .matcher(link.attr("onclick"));
                    if(match.find())url=match.group(1);
                }
                if(url.isBlank())continue;
                if(url.startsWith("/"))url=absolute(url);
                String hint=(link.text()+" "+link.attr("title")+" "+link.attr("class")+" "+url)
                        .toLowerCase(java.util.Locale.ROOT);
                String host=hosterName(hint,url);
                if(host!=null)result.putIfAbsent(url,new HosterOption(url,host,url,language));
            }
            return new ArrayList<>(result.values());
        });
    }
    private static String firstNonBlank(String... values){for(String value:values)if(value!=null&&!value.isBlank())return value;return "";}
    private static String hosterName(String hint,String url){
        String[][] known={{"voe","VOE"},{"dood","Doodstream"},{"streamtape","Streamtape"},
                {"vidoza","Vidoza"},{"vidmoly","Vidmoly"},{"filemoon","Filemoon"},
                {"streamwish","Streamwish"},{"lulustream","Lulustream"},{"speedfiles","SpeedFiles"},
                {"upstream","Upstream"},{"mixdrop","MixDrop"}};
        for(String[] host:known)if(hint.contains(host[0]))return host[1];
        if(hint.contains("/redirect/")){
            try{String domain=java.net.URI.create(url).getHost();return domain==null?"Stream":domain.replace("www.","");}
            catch(Exception ignored){return "Stream";}
        }
        return null;
    }
}
