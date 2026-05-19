package zw.gov.mohcc.impilo.docstore.core.ocr;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.docstore.config.DocumentStoreProperties;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves active OCR provider implementation.
 */
@Component
public class OcrProviderRouter {

    private final Map<String, OcrProvider> providers;
    private final DocumentStoreProperties properties;

    public OcrProviderRouter(List<OcrProvider> providers, DocumentStoreProperties properties) {
        this.providers = providers.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(
                        p -> normalize(p.providerType()),
                        Function.identity()));
        this.properties = properties;
    }

    public OcrProvider activeProvider() {
        String configured = normalize(properties.getOcr().getProviderType().name());
        OcrProvider provider = providers.get(configured);
        if (provider != null) {
            return provider;
        }
        OcrProvider fallback = providers.get("NOOP");
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("No OCR provider implementation available");
    }

    private String normalize(String value) {
        if (value == null) return "NOOP";
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
