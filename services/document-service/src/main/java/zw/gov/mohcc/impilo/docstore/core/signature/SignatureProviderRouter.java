package zw.gov.mohcc.impilo.docstore.core.signature;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.docstore.config.DocumentStoreProperties;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves active signature provider implementation.
 */
@Component
public class SignatureProviderRouter {

    private final Map<String, SignatureProvider> providers;
    private final DocumentStoreProperties properties;

    public SignatureProviderRouter(List<SignatureProvider> providers, DocumentStoreProperties properties) {
        this.providers = providers.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(
                        p -> normalize(p.providerType()),
                        Function.identity()));
        this.properties = properties;
    }

    public SignatureProvider activeProvider() {
        String configured = normalize(properties.getSignature().getProviderType().name());
        SignatureProvider provider = providers.get(configured);
        if (provider != null) {
            return provider;
        }
        SignatureProvider fallback = providers.get("NOOP");
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("No signature provider implementation available");
    }

    private String normalize(String value) {
        if (value == null) return "NOOP";
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
