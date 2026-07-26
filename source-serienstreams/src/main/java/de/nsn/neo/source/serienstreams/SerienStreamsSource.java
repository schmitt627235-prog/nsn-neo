package de.nsn.neo.source.serienstreams;

import de.nsn.neo.model.ContentType;
import de.nsn.neo.model.SourceId;
import de.nsn.neo.source.SourceMetadata;
import de.nsn.neo.source.HtmlSourceProvider;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;
import de.nsn.neo.source.Callback;
import de.nsn.neo.source.HomeSection;
import de.nsn.neo.model.MediaItem;
import de.nsn.neo.model.HosterOption;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SerienStreamsSource extends HtmlSourceProvider {
    public static final SourceMetadata METADATA = new SourceMetadata(
            SourceId.SERIENSTREAMS, "SerienStreams", "http://186.2.175.5/", ContentType.SERIES, true);
    public SerienStreamsSource() { super(SourceId.SERIENSTREAMS, METADATA.baseUrl, ContentType.SERIES, true); enableFormLogin(); }
    @Override protected String contentPathSelector() { return "a[href^=/serie/],a[href*='186.2.175.5/serie/']"; }
    @Override protected boolean accepts(Element link, String title, String url) { return url.contains("/serie/"); }
    @Override protected List<MediaItem> cards(Element root,int limit){
        Map<String,MediaItem> unique=new LinkedHashMap<>();
        for(Element article:root.select(".card-mini:has(a[href^=/serie/]), .cover-card:has(a[href^=/serie/]), article:has(a[href^=/serie/]), article:has(a[href*='186.2.175.5/serie/'])")){
            Element link=article.selectFirst("a[href^=/serie/],a[href*='186.2.175.5/serie/']");
            Element image=article.selectFirst("img[data-src],img[src]");if(image==null)image=article.selectFirst("source[data-srcset],source[srcset]");
            if(link==null||image==null)continue;String url=link.absUrl("href");if(url.isBlank())url=absolute(link.attr("href"));
            String title=image.attr("alt").replaceFirst("(?i)\\s+backdrop\\s*$","").trim();if(title.isBlank()){Element heading=article.selectFirst("h1,h2,h3,h4");title=heading==null?link.text().trim():heading.text().trim();}
            String poster=imageUrl(image);
            if(poster==null){
                try{MediaItem detail=parseDetail(load(url),url);poster=detail.posterUrl;}catch(Exception ignored){}
            }
            if(title.isBlank()||poster==null||poster.isBlank()||!accepts(link,title,url))continue;
            unique.putIfAbsent(url,new MediaItem(url,id(),ContentType.SERIES,title,"",poster,poster,url,List.of(),null,null));
            if(unique.size()>=limit)break;
        }
        if(unique.isEmpty())return super.cards(root,limit);return new ArrayList<>(unique.values());
    }
    @Override public void calendar(Callback<List<MediaItem>> callback){async(callback,()->{
        String json=transport.get(METADATA.baseUrl+"api/calendar");org.json.JSONObject root=new org.json.JSONObject(json);
        String day=java.time.LocalDate.now().toString();org.json.JSONArray entries=root.optJSONArray(day);List<MediaItem> result=new ArrayList<>();if(entries==null)return result;
        LinkedHashSet<String> seen=new LinkedHashSet<>();for(int i=0;i<entries.length();i++){org.json.JSONObject ep=entries.getJSONObject(i);String url=absolute(ep.optString("url"));
            String title=ep.optString("title")+" S"+String.format("%02d",ep.optInt("season"))+"E"+String.format("%02d",ep.optInt("episode"));String time=ep.optString("time","00:00");
            String key=url+"|"+time;if(!seen.add(key))continue;String status=ep.optBoolean("released")?"✓ ":"";result.add(new MediaItem(url,id(),ContentType.SERIES,title,status+time+" · SerienStreams",null,null,url,List.of(),null,null));}
        return result;
    });}

    private List<MediaItem> calendarItems(Document doc,String source,int limit){
        List<MediaItem> result=new ArrayList<>();LinkedHashSet<String> seen=new LinkedHashSet<>();
        for(Element link:doc.select("a[href*=staffel-][href*=episode-]")){
            String url=link.absUrl("href");if(url.isBlank())url=absolute(link.attr("href"));if(!seen.add(url))continue;
            Element row=link.closest("li,tr,.calendar-entry,.latest-episode-row,div");String text=row==null?link.text():row.text();
            java.util.regex.Matcher tm=java.util.regex.Pattern.compile("\\b([0-2]?\\d:[0-5]\\d)\\b").matcher(text);String time=tm.find()?tm.group(1):"--:--";
            String title=link.attr("title");if(title.isBlank()){Element named=link.selectFirst(".ep-title-text,.ep-title");title=named==null?link.text():named.text();}
            if(title.isBlank())title=text.replace(time,"").trim();
            result.add(new MediaItem(url,id(),ContentType.SERIES,title,time+" · "+source,null,null,url,List.of(),null,null));if(result.size()>=limit)break;
        }return result;
    }
    @Override public void home(Callback<List<HomeSection>> callback){async(callback,()->{
        Document doc=load(homeUrl());List<HomeSection> sections=new ArrayList<>();
        List<MediaItem> latest=latestEpisodes(doc,50);
        if(latest.isEmpty())latest=sectionCards(doc,"Neue Episoden",50);
        if(latest.isEmpty())latest=sectionCards(doc,"Neu auf SerienStream",50);
        add(sections,"series-latest","Neueste Serien",latest);
        List<MediaItem> popular=sectionCards(doc,"Die Beliebtesten",40);
        if(popular.isEmpty())popular=sectionCards(doc,"Aktuell beliebt",40);
        add(sections,"series-popular","Beliebte Serien",popular);
        if(sections.isEmpty())sections.add(new HomeSection("series-home","Beliebte Serien",cards(doc,60)));
        return sections;
    });}
    private List<MediaItem> latestEpisodes(Document doc,int limit){
        Map<String,MediaItem> unique=new LinkedHashMap<>();
        Map<String,String> posterCache=new LinkedHashMap<>();
        for(Element link:doc.select("a.latest-episode-row[href*=/serie/][href*=staffel-][href*=episode-]")){
            String url=link.absUrl("href");if(url.isBlank())url=absolute(link.attr("href"));
            String key=url.replaceFirst("[?#].*$","").replaceFirst("/+$","");
            if(!unique.containsKey(key)){
                Element named=link.selectFirst(".ep-title-text,.ep-title");
                String title=named==null?link.attr("title").trim():named.text().trim();
                java.util.regex.Matcher parts=java.util.regex.Pattern
                        .compile("/staffel-(\\d+)/episode-(\\d+)",java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(key);
                String code="";
                if(parts.find())try{
                    code=String.format(java.util.Locale.ROOT,"S%02dE%02d",
                            Integer.parseInt(parts.group(1)),Integer.parseInt(parts.group(2)));
                }catch(NumberFormatException ignored){}
                String detailUrl=key.replaceFirst("/staffel-\\d+.*$","");
                String poster=posterCache.get(detailUrl);
                if(poster==null)try{
                    MediaItem detail=parseDetail(load(detailUrl),detailUrl);
                    poster=detail.posterUrl;
                }catch(Exception ignored){}
                if(poster!=null)posterCache.put(detailUrl,poster);
                if(!title.isBlank())unique.put(key,new MediaItem(key,id(),ContentType.SERIES,title,
                        code.isBlank()?"Neueste Episode":code+" · Neueste Episode",
                        poster,poster,detailUrl,List.of(),null,null));
            }
            if(unique.size()>=limit)break;
        }
        return new ArrayList<>(unique.values());
    }
    @Override public void search(String query,Callback<List<MediaItem>> callback){async(callback,()->cards(load(METADATA.baseUrl+"suche?term="+java.net.URLEncoder.encode(query,"UTF-8")),100));}
    private static void add(List<HomeSection> target,String id,String title,List<MediaItem> items){if(items!=null&&!items.isEmpty())target.add(new HomeSection(id,title,items));}

    @Override public void languages(String contentId,String episodeId,Callback<List<String>> callback){async(callback,()->{
        Document doc=load(absolute(episodeId==null?contentId:episodeId));LinkedHashSet<String> result=new LinkedHashSet<>();
        for(Element button:doc.select("button.link-box[data-language-label]")){String label=button.attr("data-language-label").trim();if(!label.isEmpty())result.add(label);}
        return new ArrayList<>(result);
    });}

    @Override public void hosters(String contentId,String episodeId,String language,Callback<List<HosterOption>> callback){async(callback,()->{
        Document doc=load(absolute(episodeId==null?contentId:episodeId));List<HosterOption> result=new ArrayList<>();
        for(Element button:doc.select("button.link-box[data-play-url]")){
            String label=button.attr("data-language-label").trim();if(language!=null&&!language.equalsIgnoreCase(label))continue;
            String url=absolute(button.attr("data-play-url"));String name=button.attr("data-provider-name").trim();if(name.isEmpty())name=button.text().trim();
            if(!url.isBlank())result.add(new HosterOption(button.attr("data-link-id"),name,url,label));
        }
        return result;
    });}
}
