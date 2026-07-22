package de.nsn.neo.model;

public final class Episode {
    public final String id;
    public final int season;
    public final int number;
    public final String title;
    public final String description;
    public final String thumbnailUrl;
    public final long durationMs;

    public Episode(String id, int season, int number, String title, String description,
                   String thumbnailUrl, long durationMs) {
        this.id = id; this.season = season; this.number = number;
        this.title = title; this.description = description;
        this.thumbnailUrl = thumbnailUrl; this.durationMs = durationMs;
    }
}

