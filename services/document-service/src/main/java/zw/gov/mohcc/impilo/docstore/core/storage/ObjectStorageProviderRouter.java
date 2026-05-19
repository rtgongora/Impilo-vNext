package zw.gov.mohcc.impilo.docstore.core.storage;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.docstore.config.DocumentStoreProperties;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes binary storage operations to the configured provider implementation.
 */
@Component
public class ObjectStorageProviderRouter {

    private final Map<String, ObjectStorageProvider> providers;
    private final DocumentStoreProperties properties;

    public ObjectStorageProviderRouter(List<ObjectStorageProvider> providers,
                                       DocumentStoreProperties properties) {
        this.providers = providers.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(
                        p -> normalize(p.providerType()),
                        Function.identity()));
        this.properties = properties;
    }

    public ObjectStorageProvider activeProvider() {
        String configured = normalize(properties.getStorage().getProviderType().name());
        ObjectStorageProvider provider = providers.get(configured);
        if (provider != null) {
            return provider;
        }
        ObjectStorageProvider fallback = providers.get("MINIO");
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("No storage provider implementation available");
    }

    private static String normalize(String value) {
        if (value == null) {
            return "MINIO";
        }
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
