package de.nsn.neo.session;

import de.nsn.neo.model.SourceId;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.Objects;

/** A source-owned cookie jar. Instances must never be shared between providers. */
public final class SourceSession {
    private final SourceId source;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private volatile boolean loggedIn;

    public SourceSession(SourceId source) { this.source = Objects.requireNonNull(source); }
    public SourceId source() { return source; }
    public CookieManager cookies() { return cookies; }
    public boolean isLoggedIn() { return loggedIn; }
    public void setLoggedIn(boolean value) { loggedIn = value; }
    public void clear() { cookies.getCookieStore().removeAll(); loggedIn = false; }
}

