package de.nsn.neo.source.aniworld;

import de.nsn.neo.model.ContentType;
import de.nsn.neo.model.SourceId;
import de.nsn.neo.source.SourceMetadata;
import de.nsn.neo.source.HtmlSourceProvider;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;
import de.nsn.neo.source.Callback;
import de.nsn.neo.source.HomeSection;
import de.nsn.neo.model.MediaItem;
import de.nsn.neo.model.Episode;
import de.nsn.neo.model.HosterOption;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URI;

public final class AniWorldSource extends HtmlSourceProvider {
    public static final SourceMetadata METADATA = new SourceMetadata(
            SourceId.ANIWORLD, "AniWorld", "https://aniworld.to/", ContentType.ANIME, true);
    private static final List<String> DOMAIN_CANDIDATES = List.of("https://aniworld.to/", "https://aniworld.cc/");
    private volatile String activeBase = METADATA.baseUrl;
    public AniWorldSource() { super(SourceId.ANIWORLD, METADATA.baseUrl, ContentType.ANIME, true); enableFormLogin(); }
    @Override protected String homeUrl() { return activeBase + "home"; }
    @Override protected String absolute(String path) { try { return URI.create(activeBase).resolve(path).toString(); } catch (Exception ignored) { return path; } }
    @Override protected String contentPathSelector() { return "a[href*=/anime/stream/]"; }
    @Override protected boolean accepts(Element link, String title, String url) { return url.contains("/anime/stream/"); }
    @Override public void home(Callback<List<HomeSection>> callback){async(callback,()->{
        Document doc=null;Exception last=null;
        for(String candidate:DOMAIN_CANDIDATES){try{Document checked=load(candidate+"home");if(!isAniWorldPage(checked))continue;activeBase=candidate;doc=checked;break;}catch(Exception error){last=error;}}
        if(doc==null){if(last!=null)throw last;throw new IllegalStateException("AniWorld-Seite enthält keine erwarteten Anime-Merkmale");}
        List<HomeSection> sections=new ArrayList<>();
        add(sections,"ani-latest","Die neuesten Anime-Episoden",latestEpisodes(doc,50));
        add(sections,"ani-popular","Aktuell beliebte Anime",sectionCards(doc,"Beliebt bei AniWorld",30));
        if(sections.isEmpty())sections.add(new HomeSection("ani-home","Beliebte Anime",cards(doc,60)));
        return sections;
    });}
    private static boolean isAniWorldPage(Document doc){
        String title=doc.title().toLowerCase(java.util.Locale.ROOT);
        return title.contains("aniworld") && doc.select("a[href*=/anime/stream/]").size()>=2;
    }
    private List<MediaItem> latestEpisodes(Document doc,int limit){
        Element list=null;
        for(Element heading:doc.select("h1,h2,h3,h4")){
            if(!heading.text().toLowerCase(java.util.Locale.ROOT).contains("50 neuesten episoden"))continue;
            Element parent=heading.parent();
            for(int depth=0;parent!=null&&depth<4;depth++,parent=parent.parent()){
                list=parent.selectFirst(".newEpisodeList");
                if(list!=null)break;
            }
            if(list!=null)break;
        }
        if(list==null)return List.of();
        Map<String,MediaItem> unique=new LinkedHashMap<>();
        Map<String,String> posterCache=new LinkedHashMap<>();
        for(Element link:list.select("a[href*=/anime/stream/],a[href*='/anime/stream/']")){
            String url=link.absUrl("href");if(url.isBlank())url=absolute(link.attr("href"));
            Element name=link.selectFirst("strong");String title=name==null?cleanText(link.text()):cleanText(name.text());
            if(title.isBlank()||url.isBlank())continue;
            String episodeKey=url.replaceFirst("[?#].*$","").replaceFirst("/+$","");
            if(unique.containsKey(episodeKey))continue;
            String episodeCode=episodeCode(episodeKey);
            Element tag=link.selectFirst(".listTag");String episode=tag==null?"":cleanText(tag.text());
            String description=(episodeCode.isBlank()?"":episodeCode+" · ")
                    +(episode.isBlank()?"Neueste Episode":"Neueste Episode · "+episode);
            String detailUrl=url.replaceFirst("/staffel-\\d+.*$","");
            Element image=findCardImage(link);String poster=imageUrl(image);
            if(poster==null&&posterCache.containsKey(detailUrl))poster=posterCache.get(detailUrl);
            if(poster==null)poster=detailPoster(url);
            if(poster!=null)posterCache.put(detailUrl,poster);
            unique.put(episodeKey,new MediaItem(episodeKey,id(),ContentType.ANIME,title,description,poster,poster,detailUrl,List.of(),null,null));
            if(unique.size()>=limit)break;
        }
        return new ArrayList<>(unique.values());
    }
    private static String episodeCode(String url){
        java.util.regex.Matcher matcher=java.util.regex.Pattern
                .compile("/staffel-(\\d+)/episode-(\\d+)",java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(url);
        if(!matcher.find())return "";
        try{
            return String.format(java.util.Locale.ROOT,"S%02dE%02d",
                    Integer.parseInt(matcher.group(1)),Integer.parseInt(matcher.group(2)));
        }catch(NumberFormatException ignored){
            return "";
        }
    }
    private String detailPoster(String detailUrl){
        try{
            Document detail=load(detailUrl);
            Element cover=detail.selectFirst(
                    ".seriesCoverBox img, .seriesCover img, img[itemprop=image], img[src*=/public/img/cover/], img[data-src*=/public/img/cover/]");
            String poster=imageUrl(cover);
            if(poster!=null)return poster;
            Element meta=detail.selectFirst("meta[property=og:image],meta[name=twitter:image]");
            if(meta!=null&&!meta.attr("content").isBlank())return absolute(meta.attr("content"));
        }catch(Exception ignored){}
        return null;
    }
    private static void add(List<HomeSection> target,String id,String title,List<MediaItem> items){if(items!=null&&!items.isEmpty())target.add(new HomeSection(id,title,items));}
    @Override public void episodes(String contentId,Callback<List<Episode>> callback){async(callback,()->{
        String base=absolute(contentId).replaceFirst("/staffel-\\d+.*$","");
        Document overview=load(base);Map<String,Episode> all=new LinkedHashMap<>();
        for(Episode episode:parseEpisodes(overview))all.put(episode.id,episode);
        java.util.LinkedHashSet<String> seasons=new java.util.LinkedHashSet<>();
        for(Element link:overview.select("a[href*=staffel-]")){
            String url=link.absUrl("href");
            if(url.matches("(?i).*/staffel-\\d+/?$"))seasons.add(url);
        }
        for(String season:seasons)for(Episode episode:parseEpisodes(load(season)))all.put(episode.id,episode);
        return new ArrayList<>(all.values());
    });}
    @Override public void search(String query,Callback<List<MediaItem>> callback){async(callback,()->{
        Map<String,String> fields=new LinkedHashMap<>();fields.put("keyword",query);
        org.json.JSONArray data=new org.json.JSONArray(transport.postForm(activeBase+"ajax/search",fields));
        List<MediaItem> result=new ArrayList<>();
        for(int i=0;i<data.length();i++){org.json.JSONObject hit=data.optJSONObject(i);if(hit==null)continue;
            String title=org.jsoup.Jsoup.parse(hit.optString("title")).text().trim();String url=absolute(hit.optString("link"));if(title.isBlank()||url.isBlank()||url.contains("/staffel-")||!url.contains("/anime/stream/"))continue;
            String description=org.jsoup.Jsoup.parse(hit.optString("description")).text().trim();
            String poster=searchPoster(hit);
            if(poster==null){
                try{MediaItem detail=parseDetail(load(url),url);poster=detail.posterUrl;}catch(Exception ignored){}
            }
            result.add(new MediaItem(url,id(),ContentType.ANIME,title,description,poster,poster,url,List.of(),null,null));
        }return result;
    });}
    private String searchPoster(org.json.JSONObject hit){
        String[] keys={"cover","coverUrl","cover_url","poster","posterUrl","poster_url",
                "image","imageUrl","image_url","thumbnail","thumb"};
        for(String key:keys){
            String raw=hit.optString(key);if(raw==null||raw.isBlank())continue;
            Document fragment=org.jsoup.Jsoup.parseBodyFragment(raw,activeBase);
            Element image=fragment.selectFirst("img");
            String value=image==null?raw:imageUrl(image);
            if(value!=null&&!value.isBlank()&&!value.startsWith("data:"))return absolute(value);
        }
        return null;
    }
    @Override public void languages(String contentId,String episodeId,Callback<List<String>> callback){async(callback,()->{
        Document doc=load(absolute(episodeId==null?contentId:episodeId));
        java.util.LinkedHashSet<String> result=new java.util.LinkedHashSet<>();
        for(Element item:doc.select("[data-lang-key]")){String label=aniLanguageLabel(item.attr("data-lang-key"),item);if(!label.isBlank())result.add(label);}
        if(result.isEmpty())for(Element flag:doc.select(".editFunctions img.flag, .changeLanguageBox img.flag, img.flag")){Element owner=flag.closest("[data-lang-key]");String label=aniLanguageLabel(owner==null?"":owner.attr("data-lang-key"),flag);if(!label.isBlank())result.add(label);}
        if(result.isEmpty())return superLanguages(doc);
        return new ArrayList<>(result);
    });}
    @Override protected String resolveLanguageKey(Document doc,String language){
        String wanted=aniLanguageCategory("",new Element(org.jsoup.parser.Tag.valueOf("div"),"").text(language));
        for(Element item:doc.select("[data-lang-key]"))if(wanted.equals(aniLanguageCategory(item.attr("data-lang-key"),item)))return item.attr("data-lang-key");
        return super.resolveLanguageKey(doc,language);
    }
    @Override public void hosters(String contentId,String episodeId,String language,Callback<List<HosterOption>> callback){
        super.hosters(contentId,episodeId,language,new Callback<List<HosterOption>>(){
            @Override public void onSuccess(List<HosterOption> values){
                Map<String,HosterOption> usable=new LinkedHashMap<>();
                for(HosterOption value:values){
                    if(value==null||value.name==null||value.url==null||value.url.isBlank())continue;
                    usable.put(value.name.trim().toLowerCase(java.util.Locale.ROOT),value);
                }
                callback.onSuccess(new ArrayList<>(usable.values()));
            }
            @Override public void onError(Throwable error){callback.onError(error);}
        });
    }
    private static String aniLanguageCategory(String key,Element element){
        String hint=(element.attr("title")+" "+element.attr("alt")+" "+element.attr("src")+" "+element.className()+" "+element.text()).toLowerCase(java.util.Locale.ROOT);
        if(hint.contains("engl")||hint.contains("english")||hint.contains("engsub"))return "en-sub";
        if(hint.contains("untertitel")||hint.contains("german-sub")||hint.contains("gersub")||hint.contains("japanese-german"))return "de-sub";
        if(hint.contains("synchron")||hint.contains("dub")||hint.contains("deutsch")||hint.contains("german"))return "dub";
        if("3".equals(key))return "en-sub";if("2".equals(key))return "de-sub";if("1".equals(key))return "dub";return "";
    }
    private static String aniLanguageLabel(String key,Element element){String c=aniLanguageCategory(key,element);if("en-sub".equals(c))return "Englisch (Untertitel)";if("de-sub".equals(c))return "Deutsch (Untertitel)";if("dub".equals(c))return "Deutsch (Synchronisiert)";return "";}
    private List<String> superLanguages(Document doc){
        java.util.LinkedHashSet<String> result=new java.util.LinkedHashSet<>();
        for(Element flag:doc.select("[data-lang-key]")){String key=flag.attr("data-lang-key");if("1".equals(key))result.add("Deutsch (Synchronisiert)");else if("2".equals(key))result.add("Deutsch (Untertitel)");else if("3".equals(key))result.add("Englisch (Untertitel)");}
        return new ArrayList<>(result);
    }
    @Override public void calendar(Callback<List<MediaItem>> callback){async(callback,()->{
        Document doc;try{doc=load(activeBase+"animekalender");}catch(Exception ignored){doc=load(homeUrl());}
        List<MediaItem> result=new ArrayList<>();java.util.LinkedHashSet<String> seen=new java.util.LinkedHashSet<>();
        Element today=null;
        for(Element heading:doc.select("h1,h2,h3,h4"))if(heading.text().contains("(heute)")){
            Element next=heading.nextElementSibling();
            if(next!=null&&next.hasClass("seriesListContainer"))today=next;
            else if(heading.parent()!=null)today=heading.parent().selectFirst(".seriesListContainer");
            if(today!=null)break;
        }
        if(today==null)today=doc.selectFirst(".seriesListContainer");
        if(today==null)today=doc;
        for(Element cell:today.select("div.col-md-15,div.col-sm-3,div.col-xs-6")){
            Element link=cell.selectFirst("a[href*=/anime/stream/]");if(link==null)continue;
            String url=link.absUrl("href");if(url.isBlank())url=activeBase+link.attr("href").replaceFirst("^/","");
            String detailUrl=url.replaceFirst("/staffel-\\d+.*$","");String text=cell.text();
            java.util.regex.Matcher tm=java.util.regex.Pattern.compile("\\b([0-2]?\\d:[0-5]\\d)\\b").matcher(text);String time=tm.find()?tm.group(1):"--:--";
            Element named=cell.selectFirst(".seriesTitle");String title=named==null?"":named.text().trim();
            String seasonEpisode="";java.util.regex.Matcher se=java.util.regex.Pattern.compile("\\bS(\\d+)E(\\d+)\\b",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
            if(se.find())seasonEpisode="S"+Integer.parseInt(se.group(1))+" E"+Integer.parseInt(se.group(2));
            if(title.isBlank()){Element image=cell.selectFirst("noscript img[title],img[alt]");title=image==null?detailUrl:image.attr("alt").replaceFirst("\\s+Cover$","").trim();}
            String key=(detailUrl+"|"+seasonEpisode+"|"+time).toLowerCase(java.util.Locale.ROOT);if(!seen.add(key))continue;
            boolean released=cell.selectFirst(".fa-check,[title*=online],use[href*=check]")!=null||cell.html().contains("#63d02b");
            String description=(released?"✓ ":"")+time+(seasonEpisode.isBlank()?"":" · "+seasonEpisode)+" · AniWorld";
            result.add(new MediaItem(key,id(),ContentType.ANIME,title,description,null,null,detailUrl,List.of(),null,null));
        }return result;
    });}
}
