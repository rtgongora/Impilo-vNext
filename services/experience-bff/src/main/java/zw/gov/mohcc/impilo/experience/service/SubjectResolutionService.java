package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The BFF's single seam for Health ID → CPID resolution (Identity Contract §7.2).
 *
 * <p>The BFF is a composition tier: it may hold the HID (for the VITO banner call)
 * and the CPID (for PCT/BUTANO clinical calls) transiently in the same request, but
 * it never fabricates one from the other. Every CPID comes from tshepo-identity's
 * mapping — the pre-contract {@code cpid = healthId} shortcut is a violation.</p>
 *
 * <p>A small TTL cache keeps banner+timeline composition from hitting the mapping
 * endpoint on every fan-out call; entries are short-lived so merge redirects are
 * picked up quickly.</p>
 */
@Service
public class SubjectResolutionService {

    private static final Logger log = LoggerFactory.getLogger(SubjectResolutionService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final int CACHE_MAX = 5_000;

    private record CachedCpid(String cpid, Instant expiresAt) { }

    private final TshepoIdentityServiceClient identityClient;
    private final Map<String, CachedCpid> cache = new ConcurrentHashMap<>();

    public SubjectResolutionService(TshepoIdentityServiceClient identityClient) {
        this.identityClient = identityClient;
    }

    /**
     * Resolve the CPID for a Health ID, creating the mapping if absent.
     *
     * @return the CPID, or null when resolution fails (caller decides whether the
     *         flow can proceed without clinical context)
     */
    public String cpidForHealthId(String healthId) {
        if (healthId == null || healthId.isBlank()) {
            return null;
        }
        CachedCpid cached = cache.get(healthId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.cpid();
        }
        try {
            JsonNode mapping = identityClient.getMappingByHealthId(healthId);
            String cpid = mapping != null ? mapping.path("cpid").asText(null) : null;
            if (cpid == null) {
                // No mapping yet — mint one (find-or-create is idempotent server-side).
                JsonNode created = identityClient.generateCpid(Map.of("healthId", healthId,
                        "tenantId", tenantIdFromContextOrDefault()));
                cpid = created != null ? created.path("cpid").asText(null) : null;
            }
            if (cpid != null) {
                if (cache.size() > CACHE_MAX) {
                    cache.clear();
                }
                cache.put(healthId, new CachedCpid(cpid, Instant.now().plus(CACHE_TTL)));
            }
            return cpid;
        } catch (Exception e) {
            log.warn("CPID resolution failed for healthId ...{}: {}",
                    healthId.length() > 6 ? healthId.substring(healthId.length() - 6) : healthId,
                    e.getMessage());
            return null;
        }
    }

    /** Evict a cached resolution (e.g. after a merge event). */
    public void evict(String healthId) {
        if (healthId != null) {
            cache.remove(healthId);
        }
    }

    private static String tenantIdFromContextOrDefault() {
        try {
            var attrs = (org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String tenant = attrs.getRequest().getHeader("X-Tenant-ID");
                if (tenant != null && !tenant.isBlank()) {
                    return tenant;
                }
            }
        } catch (Exception ignored) {
            // fall through to default
        }
        return "00000000-0000-0000-0000-000000000001";
    }
}
