package de.nsn.neo.source;

import de.nsn.neo.model.ContentType;
import de.nsn.neo.model.SourceId;

public final class SourceMetadata {
    public final SourceId id;
    public final String displayName;
    public final String baseUrl;
    public final ContentType contentType;
    public final boolean requiresLogin;

    public SourceMetadata(SourceId id, String displayName, String baseUrl, ContentType contentType, boolean requiresLogin) {
        this.id=id; this.displayName=displayName; this.baseUrl=baseUrl; this.contentType=contentType; this.requiresLogin=requiresLogin;
    }
}

