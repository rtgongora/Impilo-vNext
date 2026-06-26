package zw.gov.mohcc.impilo.tshepo.identity.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.IdentifierResolveRequest;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.IdentifierResolveResponse;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.IdMappingEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.IdMappingRepository;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * C3 silent identifier resolution (person-first login hardening).
 *
 * <p>Resolves a login-supplied identifier to a person anchor through the silent
 * chain. Every outcome — resolved or not — returns the <b>same response shape</b>
 * and is floored to a <b>constant minimum latency</b>, so a caller cannot probe
 * identifier existence by inspecting the body or timing the call (anti-enumeration,
 * policies IDRES-SILENT-CHAIN + LOGIN-ANTI-ENUM, specced to track P).</p>
 *
 * <p>{@code PROVIDER_ID} and {@code COUNCIL_NUMBER} resolve a professional profile
 * to its person anchor but the response always reports {@code canAuthenticate=false}
 * (LOGIN-PROVIDERID-DENY): a provider/council number can never be a login credential.</p>
 */
@Service
public class SilentIdentifierResolutionService {

    private static final Logger log = LoggerFactory.getLogger(SilentIdentifierResolutionService.class);

    /** Constant-time floor (ms) — every resolution takes at least this long. */
    private static final long ANTI_ENUM_FLOOR_MILLIS = 120L;

    private final IdMappingRepository mappingRepo;
    private final RestTemplate vitoRestTemplate;
    private final RestTemplate varapiRestTemplate;
    private final ObjectMapper objectMapper;

    public SilentIdentifierResolutionService(IdMappingRepository mappingRepo,
                                             @Qualifier("vitoRestTemplate") RestTemplate vitoRestTemplate,
                                             @Qualifier("varapiRestTemplate") RestTemplate varapiRestTemplate,
                                             ObjectMapper objectMapper) {
        this.mappingRepo = mappingRepo;
        this.vitoRestTemplate = vitoRestTemplate;
        this.varapiRestTemplate = varapiRestTemplate;
        this.objectMapper = objectMapper;
    }

    public IdentifierResolveResponse resolve(IdentifierResolveRequest request) {
        long start = System.nanoTime();
        IdentifierResolveResponse response;
        try {
            response = route(request);
        } catch (RuntimeException ex) {
            // Never let an upstream/lookup error reveal existence: fail uniformly.
            log.debug("silent resolution error (suppressed for anti-enum): {}", ex.getMessage());
            response = IdentifierResolveResponse.notResolved();
        }
        applyConstantTimeFloor(start);
        return response;
    }

    private IdentifierResolveResponse route(IdentifierResolveRequest request) {
        String kind = request.kind() == null ? "" : request.kind().trim().toUpperCase(Locale.ROOT);
        String value = request.value() == null ? "" : request.value().trim();
        UUID tenantId = request.tenantId();
        if (value.isBlank()) {
            return IdentifierResolveResponse.notResolved();
        }

        return switch (kind) {
            case "HEALTH_ID" -> resolveHealthId(tenantId, value);
            case "IMPILO_ID" -> resolveImpiloIdHash(tenantId, value);
            case "PHONE" -> resolvePersonContact(tenantId, "phone", value);
            case "EMAIL" -> resolvePersonContact(tenantId, "email", value);
            // PROVIDER_ID / COUNCIL_NUMBER resolve a profile but never authenticate.
            case "PROVIDER_ID", "COUNCIL_NUMBER" -> resolveCouncilNumber(tenantId, value);
            // INVITE tokens are resolved by the bootstrap/self-claim flow.
            // TODO(L3 W6): resolve invite token -> pre-provisioned person anchor.
            case "INVITE" -> IdentifierResolveResponse.notResolved();
            default -> IdentifierResolveResponse.notResolved();
        };
    }

    /** HEALTH_ID: deterministic local id_mapping lookup (Health ID → CPID). */
    private IdentifierResolveResponse resolveHealthId(UUID tenantId, String value) {
        UUID healthId = parseUuidOrNull(value);
        if (healthId == null) {
            return IdentifierResolveResponse.notResolved();
        }
        Optional<IdMappingEntity> mapping = mappingRepo.findByTenantIdAndHealthId(tenantId, healthId);
        return mapping
                .map(m -> resolvedRef(m.getHealthId().toString(),
                        m.getCpid() != null ? m.getCpid().toString() : null,
                        false, "VERIFIED"))
                .orElseGet(IdentifierResolveResponse::notResolved);
    }

    /** IMPILO_ID: a lookup hash resolved by VITO to a Health ID, then to a CPID. */
    private IdentifierResolveResponse resolveImpiloIdHash(UUID tenantId, String lookupHash) {
        UUID healthId = vitoResolveLookupHash(tenantId, lookupHash);
        if (healthId == null) {
            return IdentifierResolveResponse.notResolved();
        }
        String cpid = mappingRepo.findByTenantIdAndHealthId(tenantId, healthId)
                .map(m -> m.getCpid() != null ? m.getCpid().toString() : null)
                .orElse(null);
        return resolvedRef(healthId.toString(), cpid, false, "VERIFIED");
    }

    /**
     * PHONE / EMAIL: a person contact resolved by VITO (the person SoR) to a
     * Health ID. VITO stores contacts hashed; the deterministic contact resolver
     * is not yet exposed as an internal endpoint.
     * TODO(L3): add VITO internal contact-resolve endpoint
     *   (POST /v1/internal/clients/resolve-contact {kind,value} → healthId) and
     *   call it here. Until then deny uniformly (anti-enum preserved).
     */
    private IdentifierResolveResponse resolvePersonContact(UUID tenantId, String contactKind, String value) {
        log.debug("contact resolution ({}) not yet wired to VITO — uniform deny", contactKind);
        return IdentifierResolveResponse.notResolved();
    }

    /**
     * PROVIDER_ID / COUNCIL_NUMBER: resolve a council registration number to its
     * provider's person anchor via Varapi's council/EC resolver. The resolved
     * person anchor is returned, but {@code canAuthenticate} is always
     * {@code false} — a provider/council number can never be a login credential
     * (LOGIN-PROVIDERID-DENY).
     */
    private IdentifierResolveResponse resolveCouncilNumber(UUID tenantId, String registrationNumber) {
        try {
            String url = "/v1/internal/providers/resolve-council-number?registrationNumber="
                    + java.net.URLEncoder.encode(registrationNumber, java.nio.charset.StandardCharsets.UTF_8);
            ResponseEntity<String> response = varapiRestTemplate.getForEntity(url, String.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return IdentifierResolveResponse.notResolved();
            }
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            if (!data.path("resolved").asBoolean(false)) {
                return IdentifierResolveResponse.notResolved();
            }
            JsonNode ref = data.path("providerRef");
            String healthId = textOrNull(ref, "impiloHealthId");
            UUID healthUuid = parseUuidOrNull(healthId);
            if (healthUuid == null) {
                return IdentifierResolveResponse.notResolved();
            }
            String cpid = mappingRepo.findByTenantIdAndHealthId(tenantId, healthUuid)
                    .map(m -> m.getCpid() != null ? m.getCpid().toString() : null)
                    .orElse(null);
            // canAuthenticate is ALWAYS false for provider/council identifiers.
            return new IdentifierResolveResponse(
                    true,
                    new IdentifierResolveResponse.PersonRef(healthId, cpid, false, "VERIFIED"),
                    false);
        } catch (Exception e) {
            log.debug("council-number resolution failed (suppressed): {}", e.getMessage());
            return IdentifierResolveResponse.notResolved();
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    /** Calls VITO to resolve an Impilo-ID lookup hash to a Health ID; null on any miss/error. */
    private UUID vitoResolveLookupHash(UUID tenantId, String lookupHash) {
        try {
            Map<String, Object> body = Map.of(
                    "tenantId", tenantId.toString(),
                    "lookupHash", lookupHash);
            ResponseEntity<String> response = vitoRestTemplate.postForEntity(
                    "/v1/registry/resolve", body, String.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return null;
            }
            JsonNode node = objectMapper.readTree(response.getBody()).path("data").path("healthId");
            if (node.isMissingNode() || node.isNull()) {
                return null;
            }
            return parseUuidOrNull(node.asText());
        } catch (Exception e) {
            log.debug("VITO lookup-hash resolution failed (suppressed): {}", e.getMessage());
            return null;
        }
    }

    private IdentifierResolveResponse resolvedRef(String healthId, String cpid,
                                                  boolean provisional, String assurance) {
        return new IdentifierResolveResponse(
                true,
                new IdentifierResolveResponse.PersonRef(healthId, cpid, provisional, assurance),
                // Person-anchor identifiers may authenticate; provider/council never reach here.
                true);
    }

    private void applyConstantTimeFloor(long startNanos) {
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        long remaining = ANTI_ENUM_FLOOR_MILLIS - elapsedMillis;
        if (remaining > 0) {
            try {
                Thread.sleep(remaining);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private UUID parseUuidOrNull(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
