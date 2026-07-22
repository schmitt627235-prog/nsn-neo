package de.nsn.neo.model;

import java.util.Objects;

public final class StreamRequest {
    public final SourceId source;
    public final String contentId;
    public final String episodeId;
    public final String language;
    public final String hoster;

    public StreamRequest(SourceId source, String contentId, String episodeId, String language, String hoster) {
        this.source = source; this.contentId = contentId; this.episodeId = episodeId;
        this.language = language; this.hoster = hoster;
    }

    public String stableKey() {
        return source + "|" + contentId + "|" + episodeId + "|" + language + "|" + hoster;
    }

    @Override public boolean equals(Object other) {
        return other instanceof StreamRequest && stableKey().equals(((StreamRequest) other).stableKey());
    }
    @Override public int hashCode() { return Objects.hash(stableKey()); }
}

