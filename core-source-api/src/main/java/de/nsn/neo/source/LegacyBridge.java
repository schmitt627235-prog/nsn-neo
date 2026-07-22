package de.nsn.neo.source;

import de.nsn.neo.model.ResolvedStream;
import de.nsn.neo.model.StreamRequest;

/**
 * Temporary boundary around the proven WebView/JavaScript resolver.
 * Implementations must be invisible, muted, short-lived and source-isolated.
 */
public interface LegacyBridge {
    void resolve(StreamRequest request, Callback<ResolvedStream> callback);
    void cancel(String stableRequestKey);
    void release();
}

