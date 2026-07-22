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
        add(sections,"ani-latest","Die 50 neuesten Episoden",latestEpisodes(doc,50));
        add(sections,"ani-popular","Beliebt bei AniWorld",sectionCards(doc,"Beliebt bei AniWorld",30));
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
        List<MediaItem> result=new ArrayList<>();int index=0;
        for(Element link:list.select("a[href*=/anime/stream/],a[href*='/anime/stream/']")){
            if(index>=limit)break;
            String url=link.absUrl("href");if(url.isBlank())url=absolute(link.attr("href"));
            Element name=link.selectFirst("strong");String title=name==null?cleanText(link.text()):cleanText(name.text());
            if(title.isBlank()||url.isBlank())continue;
            Element tag=link.selectFirst(".listTag");String episode=tag==null?"":cleanText(tag.text());
            String description=episode.isBlank()?"Neueste Episode":"Neueste Episode Â· "+episode;
            result.add(new MediaItem(url+"#latest-"+index++,id(),ContentType.ANIME,title,description,null,null,url,List.of(),null,null));
        }
        return result;
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
            String title=org.jsoup.Jsoup.parse(hit.optString("title")).text().trim();String url=absolute(hit.optString("link"));if(title.isBlank()||url.isBlank()||url.contains("/staffel-"))continue;
            String description=org.jsoup.Jsoup.parse(hit.optString("description")).text().trim();
            result.add(new MediaItem(url,id(),ContentType.ANIME,title,description,null,null,url,List.of(),null,null));
        }return result;
    });}
    @Override public void languages(String contentId,String episodeId,Callback<List<String>> callback){async(callback,()->{
        Document doc=load(absolute(episodeId==null?contentId:episodeId));
        java.util.LinkedHashSet<String> result=new java.util.LinkedHashSet<>();
        for(Element flag:doc.select(".editFunctions img.flag, .changeLanguageBox img.flag, img.flag")){
            String hint=(flag.attr("title")+" "+flag.attr("alt")+" "+flag.attr("src")).toLowerCase(java.util.Locale.ROOT);
            if(hint.contains("engl")||hint.contains("english"))result.add("Englisch (Untertitel)");
            else if(hint.contains("untertitel")||hint.contains("japanese-german"))result.add("Deutsch (Untertitel)");
            else if(hint.contains("deutsch")||hint.contains("german"))result.add("Deutsch (Synchronisiert)");
        }
        if(result.isEmpty())return superLanguages(doc);
        return new ArrayList<>(result);
    });}
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
