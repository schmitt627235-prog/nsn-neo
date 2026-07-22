package de.serienstreams.neo.tv;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Lightweight, privacy-first request blocker inspired by Brave Shields. */
final class AdBlocker {
    private static boolean initialized;
    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "amazon-adsystem.com", "criteo.com",
        "criteo.net", "taboola.com", "outbrain.com", "popads.net",
        "popcash.net", "propellerads.com", "onclicka.com", "exoclick.com",
        "juicyads.com", "trafficjunky.net", "adsterra.com", "hilltopads.net",
        "google-analytics.com", "googletagmanager.com", "hotjar.com",
        "clarity.ms", "scorecardresearch.com", "facebook.net"
        , "adnxs.com", "adsrvr.org", "smartadserver.com", "mgid.com",
        "revcontent.com", "bidvertiser.com", "clickadu.com", "richads.com",
        "pushground.com", "evadav.com", "ad-maven.com", "zeropark.com",
        "trafficstars.com", "onclickperformance.com", "popunderjs.club",
        "interdependentvaluable.com", "portalfluently.com", "static.ads-twitter.com",
        "imasdk.googleapis.com", "cd.connatix.com", "interactiveadvertisingbureau.com"
    ));

    private static final String[] AD_PATH_HINTS = {
        "/ads/", "/adserver", "/adserve", "/banner/", "/popunder",
        "googleads", "prebid", "vast.xml", "tracking_pixel", "popunder",
        "onclickad", "zoneid=", "/afu.php", "push-notification", "/vj/?", "adblockdetector.js"
    };

    private static final WebResourceResponse EMPTY = new WebResourceResponse(
        "text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));

    static synchronized void init(Context context) {
        if (initialized) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.ad_hosts)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                String domain = parts[parts.length - 1].toLowerCase(Locale.ROOT);
                if (domain.indexOf('.') > 0 && !domain.equals("localhost") && !domain.endsWith(".localhost")) BLOCKED_HOSTS.add(domain);
            }
        } catch (IOException ignored) { }
        initialized = true;
    }

    static boolean shouldBlock(String rawUrl, boolean enabled) {
        if (!enabled || rawUrl == null) return false;
        try {
            Uri uri = Uri.parse(rawUrl);
            String host = uri.getHost();
            if (host != null) {
                host = host.toLowerCase(Locale.ROOT);
                for (String blocked : BLOCKED_HOSTS) {
                    if (host.equals(blocked) || host.endsWith("." + blocked)) return true;
                }
            }
            String normalized = rawUrl.toLowerCase(Locale.ROOT);
            for (String hint : AD_PATH_HINTS) if (normalized.contains(hint)) return true;
        } catch (RuntimeException ignored) { }
        return false;
    }

    static WebResourceResponse emptyResponse() { return EMPTY; }
}
