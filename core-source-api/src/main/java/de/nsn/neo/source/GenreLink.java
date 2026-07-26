package de.nsn.neo.source;

import de.nsn.neo.model.SourceId;

/** A genre exposed by a source, preserving the source's real target URL. */
public final class GenreLink {
    public final SourceId source;
    public final String name;
    public final String url;

    public GenreLink(SourceId source, String name, String url) {
        this.source = source;
        this.name = name;
        this.url = url;
    }
}
