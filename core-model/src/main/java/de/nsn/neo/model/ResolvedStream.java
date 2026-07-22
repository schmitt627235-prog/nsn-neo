package de.nsn.neo.model;

import java.util.Collections;
import java.util.Map;

public final class ResolvedStream {
    public final String url;
    public final String mimeType;
    public final Map<String, String> headers;
    public final String subtitleUrl;

    public ResolvedStream(String url, String mimeType, Map<String, String> headers, String subtitleUrl) {
        this.url = url; this.mimeType = mimeType;
        this.headers = headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(headers);
        this.subtitleUrl = subtitleUrl;
    }
}

