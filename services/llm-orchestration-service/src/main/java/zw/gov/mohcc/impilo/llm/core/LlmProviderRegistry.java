package zw.gov.mohcc.impilo.llm.core;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class LlmProviderRegistry {
    private final Map<String, LlmProvider> providerMap;

    public LlmProviderRegistry(List<LlmProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(p -> p.name().toLowerCase(Locale.ROOT), Function.identity()));
    }

    public LlmProvider byName(String name) {
        if (name == null) return null;
        return providerMap.get(name.toLowerCase(Locale.ROOT));
    }

    public List<LlmProvider> allByPriority() {
        return providerMap.values().stream()
                .sorted(Comparator.comparingInt(LlmProvider::priority))
                .toList();
    }
}
