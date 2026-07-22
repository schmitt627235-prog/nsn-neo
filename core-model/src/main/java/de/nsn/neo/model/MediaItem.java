package de.nsn.neo.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MediaItem {
    public final String id;
    public final SourceId source;
    public final ContentType type;
    public final String title;
    public final String description;
    public final String posterUrl;
    public final String backdropUrl;
    public final String detailUrl;
    public final List<String> genres;
    public final String year;
    public final String rating;
    public final String trailerUrl;

    public MediaItem(String id, SourceId source, ContentType type, String title,
                     String description, String posterUrl, String backdropUrl,
                     String detailUrl, List<String> genres, String year, String rating) {
        this(id, source, type, title, description, posterUrl, backdropUrl, detailUrl, genres, year, rating, null);
    }

    public MediaItem(String id, SourceId source, ContentType type, String title,
                     String description, String posterUrl, String backdropUrl,
                     String detailUrl, List<String> genres, String year, String rating,
                     String trailerUrl) {
        this.id = Objects.requireNonNull(id);
        this.source = Objects.requireNonNull(source);
        this.type = Objects.requireNonNull(type);
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.posterUrl = posterUrl;
        this.backdropUrl = backdropUrl;
        this.detailUrl = detailUrl;
        this.genres = genres == null ? Collections.emptyList() : Collections.unmodifiableList(genres);
        this.year = year;
        this.rating = rating;
        this.trailerUrl = trailerUrl;
    }
}
