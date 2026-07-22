package de.nsn.neo.source;

import de.nsn.neo.model.Episode;
import de.nsn.neo.model.HosterOption;
import de.nsn.neo.model.MediaItem;
import de.nsn.neo.model.ResolvedStream;
import de.nsn.neo.model.SourceId;
import de.nsn.neo.model.StreamRequest;
import java.util.List;
import java.util.Map;

public interface SourceProvider {
    SourceId id();
    void home(Callback<List<HomeSection>> callback);
    default void homePage(int page, Callback<List<HomeSection>> callback) { home(callback); }
    default void calendar(Callback<List<MediaItem>> callback) { callback.onSuccess(List.of()); }
    void search(String query, Callback<List<MediaItem>> callback);
    void details(String contentId, Callback<MediaItem> callback);
    void episodes(String contentId, Callback<List<Episode>> callback);
    void languages(String contentId, String episodeId, Callback<List<String>> callback);
    void hosters(String contentId, String episodeId, String language, Callback<List<HosterOption>> callback);
    void resolve(StreamRequest request, Callback<ResolvedStream> callback);
    SessionController session();
    default Map<String,String> webRequestHeaders(String url) { return Map.of(); }
}
