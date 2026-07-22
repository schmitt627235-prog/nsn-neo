package de.nsn.neo.source;

import de.nsn.neo.model.MediaItem;
import java.util.Collections;
import java.util.List;

public final class HomeSection {
    public final String id;
    public final String title;
    public final List<MediaItem> items;
    public HomeSection(String id, String title, List<MediaItem> items) {
        this.id = id; this.title = title;
        this.items = items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
    }
}

