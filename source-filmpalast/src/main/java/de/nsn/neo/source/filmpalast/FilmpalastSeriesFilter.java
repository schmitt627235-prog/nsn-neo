package de.nsn.neo.source.filmpalast;

import java.util.Locale;
import java.util.regex.Pattern;

/** Rejects episodic Filmpalast entries before they can enter search, rails or recommendations. */
public final class FilmpalastSeriesFilter {
    private static final Pattern COMPACT_EPISODE = Pattern.compile("(?i)(?:^|[^a-z0-9])s(?:taffel)?[ ._-]*\\d{1,3}[ ._-]*e(?:pisode)?[ ._-]*\\d{1,4}(?:$|[^a-z0-9])");
    private static final Pattern WORD_EPISODE = Pattern.compile("(?i)\\bstaffel\\s*\\d{1,3}\\s*(?:folge|episode)\\s*\\d{1,4}\\b");
    private static final Pattern SEASON_PATH = Pattern.compile("(?i)/(?:serie|serien|season|staffel)(?:/|$)");

    private FilmpalastSeriesFilter() {}

    public static boolean isSeries(String title, String url, String release, String category) {
        String combined = safe(title) + " " + safe(url) + " " + safe(release);
        String normalizedCategory = safe(category).toLowerCase(Locale.ROOT);
        return COMPACT_EPISODE.matcher(combined).find()
                || WORD_EPISODE.matcher(combined).find()
                || SEASON_PATH.matcher(safe(url)).find()
                || normalizedCategory.equals("serie")
                || normalizedCategory.equals("serien")
                || normalizedCategory.contains("tv-serie");
    }

    private static String safe(String value) { return value == null ? "" : value; }
}

