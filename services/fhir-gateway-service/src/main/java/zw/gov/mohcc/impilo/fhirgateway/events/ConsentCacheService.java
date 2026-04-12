package zw.gov.mohcc.impilo.fhirgateway.events;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of consent revocation decisions learned from Kafka, so the gateway
 * can deny access without a synchronous round-trip to tshepo-consent-service on every FHIR request.
 */
@Component
public class ConsentCacheService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, ConsentCacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * @param tenantId tenant UUID string
     * @param cpid      patient CPID / subject identifier (same form as consent evaluate subjectRef)
     */
    public boolean isConsentRevoked(String tenantId, String cpid) {
        if (tenantId == null || tenantId.isBlank() || cpid == null || cpid.isBlank()) {
            return false;
        }
        String key = cacheKey(tenantId, cpid);
        ConsentCacheEntry entry = cache.get(key);
        if (entry == null) {
            return false;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            cache.remove(key, entry);
            return false;
        }
        return true;
    }

    public void recordRevocation(String tenantId, String cpid) {
        if (tenantId == null || tenantId.isBlank() || cpid == null || cpid.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        String key = cacheKey(tenantId, cpid);
        cache.put(key, new ConsentCacheEntry("DENY", now, now.plus(DEFAULT_TTL)));
    }

    public void clearCache(String tenantId, String cpid) {
        if (tenantId == null || tenantId.isBlank() || cpid == null || cpid.isBlank()) {
            return;
        }
        cache.remove(cacheKey(tenantId, cpid));
    }

    private static String cacheKey(String tenantId, String cpid) {
        return tenantId.trim() + ":" + cpid.trim();
    }

    public record ConsentCacheEntry(String consentOutcome, Instant revokedAt, Instant expiresAt) {}
}
