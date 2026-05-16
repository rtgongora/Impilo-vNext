package zw.gov.mohcc.impilo.pct.core.telemedicine;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves provider-neutral telemedicine session provisioning strategies.
 */
@Component
public class TelemedicineSessionProviderRouter {

    public static final String DEFAULT_PROVIDER_TYPE = "MANAGED_PRIMARY";

    private final Map<String, TelemedicineSessionProvider> providerByType;
    private final TelemedicineProviderProperties providerProperties;

    public TelemedicineSessionProviderRouter(List<TelemedicineSessionProvider> providers,
                                             TelemedicineProviderProperties providerProperties) {
        this.providerByType = providers.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(
                        p -> normalize(p.providerType()),
                        Function.identity()));
        this.providerProperties = providerProperties;
    }

    public SessionProvisioningResult provision(String requestedProviderType,
                                               TelemedicineSessionProvider.SessionProvisioningRequest request) {
        String resolved = normalize(requestedProviderType);
        TelemedicineSessionProvider provider = providerByType.get(resolved);
        if (provider == null) {
            provider = providerByType.get(DEFAULT_PROVIDER_TYPE);
        }
        if (provider == null) {
            throw new IllegalStateException("No telemedicine session provider is configured");
        }
        try {
            return provider.provision(request);
        } catch (RuntimeException ex) {
            if (!DEFAULT_PROVIDER_TYPE.equals(resolved)
                    && providerProperties.getFallback().isAllowManagedPrimaryFallback()) {
                TelemedicineSessionProvider fallback = providerByType.get(DEFAULT_PROVIDER_TYPE);
                if (fallback != null) {
                    return fallback.provision(request);
                }
            }
            throw ex;
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PROVIDER_TYPE;
        }
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
