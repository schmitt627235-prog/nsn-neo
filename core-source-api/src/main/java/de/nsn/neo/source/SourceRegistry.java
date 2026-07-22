package de.nsn.neo.source;

import de.nsn.neo.model.SourceId;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class SourceRegistry {
    private final Map<SourceId, SourceProvider> providers = new EnumMap<>(SourceId.class);
    public void register(SourceProvider provider) { providers.put(provider.id(), provider); }
    public SourceProvider get(SourceId id) { return providers.get(id); }
    public Collection<SourceProvider> all() { return Collections.unmodifiableCollection(providers.values()); }
}
