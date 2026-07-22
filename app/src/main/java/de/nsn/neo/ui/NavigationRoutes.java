package de.nsn.neo.ui;

import android.content.Context;
import android.content.Intent;
import de.nsn.neo.model.MediaItem;
import de.nsn.neo.model.SourceId;
import de.nsn.neo.model.Episode;
import de.nsn.neo.model.HosterOption;

/** Central, typed entry points for the app's screen routes. */
public final class NavigationRoutes {
    private NavigationRoutes() {}

    public static Intent detail(Context context, SourceId source, String contentId) {
        Intent intent = new Intent(context, DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_SOURCE, source == null ? null : source.name());
        intent.putExtra(DetailActivity.EXTRA_CONTENT, contentId);
        return intent;
    }

    public static Intent detail(Context context, MediaItem item) {
        if (item == null) return detail(context, null, null);
        return detail(context, item.source, item.detailUrl == null ? item.id : item.detailUrl);
    }

    public static Intent search(Context context) {
        return new Intent(context, SearchActivity.class);
    }

    public static Intent accounts(Context context) {
        return new Intent(context, AccountsActivity.class);
    }

    public static Intent player(Context context, SourceId source, String contentId, Episode episode, HosterOption hoster, String language, String title, String posterUrl) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_HOSTER, hoster == null ? null : hoster.url);
        intent.putExtra(PlayerActivity.EXTRA_SOURCE, source == null ? null : source.name());
        intent.putExtra(PlayerActivity.EXTRA_HOSTER_NAME, hoster == null ? null : hoster.name);
        intent.putExtra(PlayerActivity.EXTRA_LANGUAGE, language); intent.putExtra(PlayerActivity.EXTRA_CONTENT, contentId);
        intent.putExtra(PlayerActivity.EXTRA_EPISODE, episode == null ? null : episode.id); intent.putExtra(PlayerActivity.EXTRA_TITLE, title);
        intent.putExtra(PlayerActivity.EXTRA_SUBTITLE, episode == null ? null : "S" + episode.season + " · E" + episode.number);
        intent.putExtra(PlayerActivity.EXTRA_POSTER, posterUrl); return intent;
    }
}
