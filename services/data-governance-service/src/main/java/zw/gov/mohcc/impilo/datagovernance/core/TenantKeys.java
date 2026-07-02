package zw.gov.mohcc.impilo.datagovernance.core;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Tenant-key derivation for data-governance storage.
 *
 * <p>The platform's trust contract treats {@code X-Tenant-ID} as an opaque
 * string ({@code moh-zw}, {@code default}); this service's schema keys tenants
 * by UUID. Parsing the header with {@link UUID#fromString} 500'd every request
 * from a canonical string tenant. Until the schema migrates to string tenant
 * keys, derive a deterministic name-based UUID for non-UUID tenants so the
 * same tenant string always maps to the same storage key.</p>
 */
public final class TenantKeys {

    private static final String NAMESPACE = "impilo-tenant:";

    private TenantKeys() {}

    public static UUID tenantUuid(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenant id is required");
        }
        try {
            return UUID.fromString(tenantId);
        } catch (IllegalArgumentException notAUuid) {
            return UUID.nameUUIDFromBytes((NAMESPACE + tenantId).getBytes(StandardCharsets.UTF_8));
        }
    }
}
