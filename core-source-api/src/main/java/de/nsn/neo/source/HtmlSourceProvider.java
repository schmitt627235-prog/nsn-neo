package de.nsn.neo.source;

import de.nsn.neo.model.ContentType;
import de.nsn.neo.model.Episode;
import de.nsn.neo.model.MediaItem;
import de.nsn.neo.model.HosterOption;
import de.nsn.neo.model.ResolvedStream;
import de.nsn.neo.model.SourceId;
import de.nsn.neo.model.StreamRequest;
import de.nsn.neo.session.SourceSession;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Shared mechanics only; every source still owns its URL rules and filtering. */
public abstract class HtmlSourceProvider implements SourceProvider {
    private final SourceId id;
    protected final String origin;
    protected final ContentType type;
    protected final SourceSession sourceSession;
    protected final HttpTransport transport;
    private SessionController sessionController;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    protected HtmlSourceProvider(SourceId id, String origin, ContentType type, boolean requiresLogin) {
        this.id = id; this.origin = origin; this.type = type;
        sourceSession = new SourceSession(id);
        transport = new HttpTransport(sourceSession);
        sessionController = new ReadOnlySessionController(sourceSession, requiresLogin);
    }
    @Override public final SourceId id() { return id; }
    @Override public final SessionController session() { return sessionController; }
    @Override public Map<String,String> webRequestHeaders(String url) {
        try {
            Map<String,List<String>> values=sourceSession.cookies().get(URI.create(url),Map.of());
            Map<String,String> result=new LinkedHashMap<>();
            for(Map.Entry<String,List<String>> entry:values.entrySet())result.put(entry.getKey(),String.join("; ",entry.getValue()));
            return result;
        } catch(Exception ignored) { return Map.of(); }
    }
    protected final void enableFormLogin() { sessionController = new FormLoginSessionController(sourceSession, transport, origin); }
    protected abstract boolean accepts(Element link, String title, String url);
    protected abstract String contentPathSelector();
    protected String homeUrl() { return origin; }

    @Override public void home(Callback<List<HomeSection>> callback) {
        async(callback, () -> {
            Document doc = load(homeUrl());
            List<MediaItem> items = cards(doc, 60);
            return List.of(new HomeSection(id.name().toLowerCase() + "-home", sectionTitle(), items));
        });
    }
    protected String sectionTitle() { return type == ContentType.MOVIE ? "Neue Filme" : type == ContentType.ANIME ? "Beliebte Anime" : "Beliebte Serien"; }
    @Override public void search(String query, Callback<List<MediaItem>> callback) {
        async(callback, () -> cards(load(origin + "search?q=" + java.net.URLEncoder.encode(query, "UTF-8")), 100));
    }
    @Override public void genres(Callback<List<GenreLink>> callback) {
        async(callback, () -> parseGenreLinks(load(homeUrl())));
    }
    @Override public void genreItems(String genreUrl, Callback<List<MediaItem>> callback) {
        async(callback, () -> cards(load(absolute(genreUrl)), 100));
    }
    @Override public void details(String contentId, Callback<MediaItem> callback) {
        async(callback, () -> parseDetail(load(absolute(contentId)), absolute(contentId)));
    }
    @Override public void episodes(String contentId, Callback<List<Episode>> callback) {
        async(callback, () -> parseEpisodes(load(absolute(contentId))));
    }
    @Override public void languages(String contentId, String episodeId, Callback<List<String>> callback) {
        async(callback, () -> {
            Document doc = load(absolute(episodeId == null ? contentId : episodeId));
            Map<String,String> found = new LinkedHashMap<>();
            for (Element flag : doc.select(".changeLanguageBox [data-lang-key], img[data-lang-key], [class*=language] [data-lang-key], .hosterSiteVideo[data-lang-key], li[data-lang-key]")) {
                String key=flag.attr("data-lang-key");if(key.isBlank())continue;
                found.putIfAbsent(languageLabel(key,flag),key);
            }
            return new ArrayList<>(found.keySet());
        });
    }
    @Override public void hosters(String contentId, String episodeId, String language, Callback<List<HosterOption>> callback) {
        async(callback, () -> {
            Document doc = load(absolute(episodeId == null ? contentId : episodeId));
            Map<String,HosterOption> result = new LinkedHashMap<>();String wantedKey=resolveLanguageKey(doc, language);
            for (Element link : doc.select("li[data-lang-key] a[href], [data-lang-key] a[href], a[href*=/redirect/], button.link-box, a.watchEpisode, .hosterSiteVideo, li[data-link-id], [data-link-id]")) {
                Element parent = link.closest("[data-lang-key]");
                if (wantedKey != null && (parent == null || !wantedKey.equals(parent.attr("data-lang-key")))) continue;
                String name = link.selectFirst("h4") != null ? link.selectFirst("h4").text() : link.text();
                String url = first(link.absUrl("href"),first(link.attr("data-link-target"),first(link.attr("data-url"),link.attr("data-href"))));
                Element linkIdOwner=link.hasAttr("data-link-id")?link:link.closest("[data-link-id]");
                if((url==null||url.isBlank())&&linkIdOwner!=null&&!linkIdOwner.attr("data-link-id").isBlank())url=absolute("redirect/"+linkIdOwner.attr("data-link-id"));
                if (url==null||url.isBlank()) url = absolute(link.attr("href"));
                if(name.isBlank())name=link.attr("title");if(name.isBlank())name="Stream";
                if (!url.isBlank()&&!url.equals(absolute(episodeId==null?contentId:episodeId))) result.putIfAbsent(url,new HosterOption(url, cleanText(name), url, language));
            }
            return new ArrayList<>(result.values());
        });
    }
    private static String languageLabel(String key,Element flag){
        String hint=(flag.attr("title")+" "+flag.attr("alt")+" "+flag.attr("src")+" "+flag.className()).toLowerCase(java.util.Locale.ROOT);
        if(hint.contains("english")||hint.contains("englisch")||hint.contains("engsub")||"3".equals(key))return "Englisch (Untertitel)";
        if(hint.contains("german-sub")||hint.contains("gersub")||hint.contains("deutsch-sub")||"2".equals(key))return "Deutsch (Untertitel)";
        if(hint.contains("german")||hint.contains("deutsch")||"1".equals(key))return "Deutsch (Synchronisiert)";
        String title=cleanText(first(flag.attr("title"),flag.attr("alt")));return title.isBlank()?"Sprache "+key:title;
    }
    protected String resolveLanguageKey(Document doc,String language){return languageKey(language);}
    private static String languageKey(String language){if(language==null)return null;String lower=language.toLowerCase(java.util.Locale.ROOT);if(lower.contains("synchron"))return "1";if(lower.contains("deutsch")&&lower.contains("untertitel"))return "2";if(lower.contains("englisch"))return "3";return language.matches("\\d+")?language:null;}
    @Override public void resolve(StreamRequest request, Callback<ResolvedStream> callback) {
        callback.onError(new UnsupportedOperationException("Dynamische Streamauflösung wird im Resolver-Modul angebunden"));
    }

    protected Document load(String url) throws Exception { return Jsoup.parse(transport.get(url), url); }
    protected List<MediaItem> cards(Element doc, int limit) {
        Map<String,MediaItem> unique = new LinkedHashMap<>();
        for (Element link : doc.select(contentPathSelector())) {
            String url = link.absUrl("href"); if (url.isBlank()) url = absolute(link.attr("href"));
            String title = cleanText(titleOf(link)); if (title.isBlank() || !accepts(link, title, url)) continue;
            Element image = findCardImage(link);
            String poster = imageUrl(image);
            unique.putIfAbsent(url, new MediaItem(url, id, type, title, "", poster, poster, url, List.of(), null, null));
            if (unique.size() >= limit) break;
        }
        return new ArrayList<>(unique.values());
    }
    protected List<MediaItem> sectionCards(Document doc, String headingText, int limit) {
        for (Element heading : doc.select("h1,h2,h3,h4")) {
            if (!cleanText(heading.text()).toLowerCase(java.util.Locale.ROOT).contains(headingText.toLowerCase(java.util.Locale.ROOT))) continue;
            Element container=heading.parent();
            for(int depth=0;container!=null&&depth<5;depth++,container=container.parent()){
                List<MediaItem> found=cards(container,limit);
                if(found.size()>=2)return found;
            }
        }
        return List.of();
    }
    protected List<GenreLink> parseGenreLinks(Document doc) {
        Map<String,GenreLink> unique = new LinkedHashMap<>();
        collectGenreAnchors(doc.select(
                ".genres a[href], .genre a[href], [class*=genre] a[href], " +
                "a[href*=genre], a[href*=kategorie], a[href*=category]"), unique);
        for (Element heading : doc.select("h1,h2,h3,h4,h5,h6")) {
            String text=cleanText(heading.text()).toLowerCase(java.util.Locale.ROOT);
            if (!text.equals("genres") && !text.equals("genre")
                    && !text.equals("kategorien") && !text.equals("kategorie")) continue;
            Element container=heading.parent();
            for(int depth=0;container!=null&&depth<3;depth++,container=container.parent()){
                int before=unique.size();
                collectGenreAnchors(container.select("a[href]"),unique);
                if(unique.size()-before>=3)break;
            }
        }
        return new ArrayList<>(unique.values());
    }
    private void collectGenreAnchors(Iterable<Element> anchors,Map<String,GenreLink> target){
        for(Element link:anchors){
            String name=cleanText(link.text());
            if(name.isBlank())name=cleanText(first(link.attr("title"),link.attr("aria-label")));
            String url=link.absUrl("href");
            if(url.isBlank())url=absolute(link.attr("href"));
            if(name.isBlank()||!allowedGenreNames().contains(normalizedGenreName(name))
                    ||url.isBlank()||url.contains("/anime/stream/")
                    ||url.contains("/serie/")||url.contains("/stream/"))continue;
            target.putIfAbsent(url,new GenreLink(id,name,url));
        }
    }
    private Set<String> allowedGenreNames(){
        if(id==SourceId.ANIWORLD)return Set.of(
                "abenteuer","action","actiondrama","actionkomodie","alltagsleben","alltagsdrama",
                "boys love","drama","ecchi","engsub","erotik","fantasy","fighting shounen",
                "ganbatte","geistergeschichten","ger","gersub","harem","horror","komodie","krimi",
                "liebesdrama","magical girl","mecha","mystery","nonsense komodie","psychodrama",
                "romantische komodie","romanze","scifi","sport","thriller","yuri",
                "ubermassige gewaltdarstellung");
        if(id==SourceId.SERIENSTREAMS)return Set.of(
                "abenteuer","action","animation","anime","comedy","dokumentation","doku soap",
                "drama","dramedy","familie","fantasy","historie","horror","jugend","kinderserie",
                "krankenhausserie","krimi","mystery","romantik","science fiction","sitcom",
                "telenovela","thriller","western","zeichentrick","k drama","reality tv","true crime");
        return Set.of(
                "abenteuer","action","animation","biographie","dokumentation","drama","englisch",
                "familie","fantasy","geschichte","horror","komodie","krieg","krimi","musik",
                "mystery","romantik","sci fi","sport","thriller","western","zeichentrick");
    }
    private static String normalizedGenreName(String value){
        return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","")
                .toLowerCase(java.util.Locale.ROOT)
                .replace("-"," ")
                .replace("_"," ")
                .replaceAll("\\s+"," ")
                .trim();
    }
    protected MediaItem parseDetail(Document doc, String url) {
        Element h1 = doc.selectFirst("h1[itemprop=name], h1");
        Element image = doc.selectFirst("img[itemprop=image], .seriesCoverBox img, .seriesCover img, img[src*=/media/images/channel/]");
        Element backdrop = doc.selectFirst("img[src*=/media/images/backdrop/], img[alt=Backdrop]");
        Element description = doc.selectFirst("[itemprop=description], [itemprop=accessibilitySummary], .seri_des, .description, .series-description, .movie-description, .detail-description, p.lead");
        List<String> genres = new ArrayList<>(); for (Element g : doc.select(".genres a, .genre a, a[href*=genre]")) if (!g.text().isBlank() && !genres.contains(g.text())) genres.add(g.text());
        String poster = imageUrl(image);
        String back = backdrop == null ? poster : imageUrl(backdrop);
        String descriptionText = "";
        if (description != null) descriptionText = first(description.attr("data-full-description"), description.text());
        if (descriptionText.isBlank()) {
            Element meta = doc.selectFirst("meta[property=og:description], meta[name=description]");
            if (meta != null) descriptionText = meta.attr("content");
        }
        String trailer = null;
        Element trailerElement = doc.selectFirst("[itemprop=trailer] video[src], [itemprop=trailer] source[src], video.trailer[src], a.trailerButton[href], [data-trailer-url]");
        if (trailerElement != null) {
            trailer = first(trailerElement.attr("data-trailer-url"), first(trailerElement.attr("src"), trailerElement.attr("href")));
            if (trailer != null && !trailer.isBlank()) trailer = absolute(trailer);
            if (!isDirectVideo(trailer)) trailer = null;
        }
        return new MediaItem(url, id, type, cleanText(h1 == null ? doc.title() : h1.text()), cleanText(descriptionText), poster, back, url, genres, null, null, trailer);
    }
    protected List<Episode> parseEpisodes(Document doc) {
        Map<String,Episode> unique = new LinkedHashMap<>();
        for (Element link : doc.select("a[href*=staffel-][href*=episode-]")) {
            String url = link.absUrl("href");
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("staffel-(\\d+).*episode-(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(url);
            if (!matcher.find()) continue;
            int season = Integer.parseInt(matcher.group(1)), number = Integer.parseInt(matcher.group(2));
            unique.putIfAbsent(url, new Episode(url, season, number, link.text().isBlank() ? "Episode " + number : link.text(), "", null, 0));
        }
        return new ArrayList<>(unique.values());
    }
    protected String absolute(String path) { try { return URI.create(origin).resolve(path).toString(); } catch (Exception ignored) { return path; } }
    protected static String titleOf(Element link) { Element title = link.selectFirst("h1,h2,h3,h4,.seriesListTitle,.seriesTitle,[itemprop=name]"); return title == null ? link.attr("title").trim() : title.text().trim(); }
    protected static String first(String a, String b) { return a != null && !a.isBlank() ? a : b; }
    protected Element findCardImage(Element link) {
        Element image = link.selectFirst("img");
        Element parent = link.parent();
        for (int depth = 0; image == null && parent != null && depth < 5; depth++, parent = parent.parent()) {
            image = parent.selectFirst("img[data-src], img[src], source[data-srcset], source[srcset]");
        }
        return image;
    }
    protected String imageUrl(Element image) {
        if (image == null) return null;
        String value = first(image.attr("data-src"), image.attr("src"));
        if (value == null || value.isBlank() || value.startsWith("data:")) {
            value = first(image.attr("data-srcset"), image.attr("srcset"));
            if (value != null && value.contains(",")) value = value.substring(0, value.indexOf(','));
            if (value != null) value = value.trim().split("\\s+")[0];
        }
        return value == null || value.isBlank() || value.startsWith("data:") ? null : absolute(value);
    }
    private static boolean isDirectVideo(String url) {
        if (url == null) return false;
        String clean = url.toLowerCase(java.util.Locale.ROOT).split("\\?")[0];
        return clean.endsWith(".mp4") || clean.endsWith(".m3u8") || clean.endsWith(".mpd") || clean.endsWith(".webm");
    }
    protected static String cleanText(String value){if(value==null)return "";String result=value.trim();for(int i=0;i<2&&(result.indexOf('\u00c3')>=0||result.indexOf('\u00c2')>=0||result.indexOf('\u00e2')>=0);i++){try{String fixed=new String(result.getBytes(StandardCharsets.ISO_8859_1),StandardCharsets.UTF_8);if(fixed.indexOf('\ufffd')>=0)break;result=fixed;}catch(Exception ignored){break;}}return result;}
    protected final <T> void async(Callback<T> callback, CheckedSupplier<T> task) { worker.execute(() -> { try { callback.onSuccess(task.get()); } catch (Throwable error) { callback.onError(error); } }); }
    protected interface CheckedSupplier<T> { T get() throws Exception; }
}
