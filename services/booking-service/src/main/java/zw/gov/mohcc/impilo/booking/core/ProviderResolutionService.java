package zw.gov.mohcc.impilo.booking.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.booking.integration.VarapiClient;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.Map;

@Service
public class ProviderResolutionService {

    private final VarapiClient varapiClient;

    public ProviderResolutionService(VarapiClient varapiClient) {
        this.varapiClient = varapiClient;
    }

    public ResolvedProvider resolve(TrustContext ctx, String providerPublicId) {
        if (providerPublicId == null || providerPublicId.isBlank()) {
            throw new IllegalArgumentException("provider_id is required");
        }
        Map<String, Object> provider = varapiClient.getProvider(ctx, providerPublicId.trim());
        if (provider.isEmpty()) {
            throw new IllegalArgumentException("Invalid provider_id: " + providerPublicId);
        }
        Object publicId = firstNonNull(provider.get("providerPublicId"), provider.get("provider_public_id"));
        if (publicId == null || !providerPublicId.trim().equalsIgnoreCase(publicId.toString())) {
            throw new IllegalArgumentException("Invalid provider_id: " + providerPublicId);
        }
        String displayName = stringOrNull(firstNonNull(
                provider.get("displayName"),
                provider.get("display_name"),
                provider.get("givenName"),
                provider.get("familyName")));
        return new ResolvedProvider(providerPublicId.trim(), displayName);
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String stringOrNull(Object value) {
        return value != null ? value.toString() : null;
    }

    public record ResolvedProvider(String providerPublicId, String providerName) {}
}
