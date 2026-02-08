package zw.gov.mohcc.impilo.costa.engine;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of all available cost engines. Resolves by CostMethodType.
 */
@Component
public class CostEngineRegistry {

    private final Map<CostMethodType, CostEngine> engines;

    public CostEngineRegistry(List<CostEngine> engineList) {
        engines = new EnumMap<>(CostMethodType.class);
        for (CostEngine engine : engineList) {
            engines.put(engine.getMethodType(), engine);
        }
    }

    public CostEngine resolve(CostMethodType type) {
        CostEngine engine = engines.get(type);
        if (engine == null) {
            throw new IllegalArgumentException("No cost engine registered for type: " + type);
        }
        return engine;
    }

    public CostEngine resolveOrDefault(CostMethodType type, CostMethodType fallback) {
        CostEngine engine = engines.get(type);
        if (engine == null) {
            engine = engines.get(fallback);
        }
        if (engine == null) {
            throw new IllegalArgumentException("No cost engine for type=" + type + " or fallback=" + fallback);
        }
        return engine;
    }

    public boolean hasEngine(CostMethodType type) {
        return engines.containsKey(type);
    }
}
