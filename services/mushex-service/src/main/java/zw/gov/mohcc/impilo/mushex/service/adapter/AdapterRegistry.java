package zw.gov.mohcc.impilo.mushex.service.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry holding all {@link PaymentRailAdapter} implementations keyed by {@link AdapterType}.
 * Adapters self-register via their {@code adapterType()} method.
 */
@Component
public class AdapterRegistry {

    private static final Logger log = LoggerFactory.getLogger(AdapterRegistry.class);

    private final Map<AdapterType, PaymentRailAdapter> adapters;

    public AdapterRegistry(List<PaymentRailAdapter> adapterList) {
        this.adapters = new EnumMap<>(AdapterType.class);
        for (PaymentRailAdapter adapter : adapterList) {
            AdapterType type = AdapterType.valueOf(adapter.adapterType());
            adapters.put(type, adapter);
            log.info("Registered payment rail adapter: {}", type);
        }
        log.info("Total payment rail adapters registered: {}", adapters.size());
    }

    /**
     * Resolve an adapter by its enum type.
     *
     * @param type the adapter type
     * @return the registered adapter
     * @throws IllegalArgumentException if no adapter is registered for the type
     */
    public PaymentRailAdapter getAdapter(AdapterType type) {
        PaymentRailAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("No payment rail adapter registered for type: " + type);
        }
        return adapter;
    }

    /**
     * Resolve an adapter by its string type name.
     *
     * @param type the adapter type name (must match an {@link AdapterType} value)
     * @return the registered adapter
     * @throws IllegalArgumentException if the type is unknown or no adapter is registered
     */
    public PaymentRailAdapter getAdapter(String type) {
        return getAdapter(AdapterType.valueOf(type));
    }

    /**
     * Report whether an adapter is registered for the given type. Used by the
     * rail-selection policy ({@code docs/design/g4-rail-selection-policy.md}) to decide
     * between a preferred-rail path and a fallback path without throwing.
     *
     * @param type the adapter type; may be {@code null}, in which case this method returns {@code false}
     * @return {@code true} if an adapter is registered for the type, {@code false} otherwise
     */
    public boolean has(AdapterType type) {
        return type != null && adapters.containsKey(type);
    }
}
