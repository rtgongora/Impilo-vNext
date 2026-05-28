package zw.gov.mohcc.impilo.experience.telemedicine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TelemedicineGovernanceService {
    private static final Logger log = LoggerFactory.getLogger(TelemedicineGovernanceService.class);

    private final TshepoAuthzServiceClient tshepoAuthzServiceClient;
    private final TshepoAuditServiceClient tshepoAuditServiceClient;

    @Value("${impilo.security.allow-anonymous:false}")
    private boolean allowAnonymous;

    @Value("${impilo.telemedicine.require-tshepo-authorize:true}")
    private boolean requireTshepoAuthorize;

    @Value("${impilo.telemedicine.tshepo-pdp-fallback-allow:false}")
    private boolean tshepoPdpFallbackAllow;

    @Value("${impilo.telemedicine.audit-ingest-enabled:true}")
    private boolean auditIngestEnabled;

    @Value("${impilo.telemedicine.require-media-consent-reference:true}")
    private boolean requireMediaConsentReference;

    @Value("${impilo.telemedicine.allow-emergency-without-consent:true}")
    private boolean allowEmergencyWithoutConsent;

    @Value("${impilo.telemedicine.allowed-purpose-of-use:TREATMENT,EMERGENCY,PAYMENT,OPERATIONS,PUBLIC_HEALTH}")
    private String allowedPurposeOfUse;

    public TelemedicineGovernanceService(
            TshepoAuthzServiceClient tshepoAuthzServiceClient,
            TshepoAuditServiceClient tshepoAuditServiceClient) {
        this.tshepoAuthzServiceClient = tshepoAuthzServiceClient;
        this.tshepoAuditServiceClient = tshepoAuditServiceClient;
    }

    public void assertGovernedRead() {
        enforce(tshepoAuthzServiceClient.telemedicineReadAllowed(), "Tshepo PDP denied telemedicine read");
    }

    public void assertGovernedMutate() {
        enforce(tshepoAuthzServiceClient.telemedicineMutateAllowed(), "Tshepo PDP denied telemedicine mutation");
    }

    public void assertBreakGlassOverrideAllowed() {
        enforce(tshepoAuthzServiceClient.telemedicineBreakGlassAllowed(), "Tshepo PDP denied telemedicine break-glass override");
    }

    public String normalizePurposeOfUse(String rawPurposeOfUse) {
        String candidate = rawPurposeOfUse == null || rawPurposeOfUse.isBlank()
                ? "TREATMENT"
                : rawPurposeOfUse.trim().replace('-', '_').toUpperCase();
        Set<String> allowed = parseAllowedPurposeOfUse();
        if (!allowed.contains(candidate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported purpose-of-use '" + candidate + "' for telemedicine workflow");
        }
        return candidate;
    }

    public void assertMediaConsentReference(String sessionType, String consentReference, String normalizedPurposeOfUse) {
        if (!requireMediaConsentReference) {
            return;
        }
        String mode = sessionType == null ? "VIDEO" : sessionType.trim().toUpperCase();
        if (!"VIDEO".equals(mode) && !"AUDIO".equals(mode)) {
            return;
        }
        if (allowEmergencyWithoutConsent && "EMERGENCY".equals(normalizedPurposeOfUse)) {
            return;
        }
        if (consentReference == null || consentReference.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "consentReference is required for governed telemedicine media sessions");
        }
    }

    private void enforce(boolean allowed, String message) {
        if (allowAnonymous || !requireTshepoAuthorize) {
            return;
        }
        if (!allowed && !tshepoPdpFallbackAllow) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
        if (!allowed) {
            log.warn("{}; continuing due to fallback policy", message);
        }
    }

    public void audit(String tenantId,
                      String correlationId,
                      String purposeOfUse,
                      String facilityId,
                      String eventType,
                      String action,
                      String outcome,
                      String actorId,
                      String actorType,
                      String subjectRef,
                      String resourceType,
                      String resourceId,
                      Map<String, Object> detail) {
        if (!auditIngestEnabled || allowAnonymous) return;
        UUID parsedTenant = parseUuid(tenantId, false);
        UUID parsedCorrelation = parseUuid(correlationId, true);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", parsedTenant);
        payload.put("eventType", eventType);
        payload.put("actorId", actorId == null || actorId.isBlank() ? "unknown" : actorId);
        payload.put("actorType", actorType == null || actorType.isBlank() ? "UNKNOWN" : actorType);
        payload.put("subjectRef", subjectRef);
        payload.put("resourceType", resourceType);
        payload.put("resourceId", resourceId);
        payload.put("action", action);
        payload.put("outcome", outcome);
        payload.put("purposeOfUse", purposeOfUse == null || purposeOfUse.isBlank() ? "TREATMENT" : purposeOfUse);
        payload.put("occurredAt", OffsetDateTime.now().toString());
        payload.put("correlationId", parsedCorrelation.toString());
        if (facilityId != null && !facilityId.isBlank()) {
            payload.put("facilityId", facilityId);
        }
        if (detail != null && !detail.isEmpty()) {
            payload.put("detail", detail);
        }
        tshepoAuditServiceClient.ingestAuditEvent(payload);
    }

    private UUID parseUuid(String raw, boolean randomFallback) {
        if (raw == null || raw.isBlank()) {
            return randomFallback ? UUID.randomUUID() : new UUID(0L, 0L);
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(raw.trim().getBytes(StandardCharsets.UTF_8));
        }
    }

    private Set<String> parseAllowedPurposeOfUse() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Arrays.stream(String.valueOf(allowedPurposeOfUse).split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .map(v -> v.replace('-', '_').toUpperCase())
                .forEach(out::add);
        if (out.isEmpty()) {
            out.add("TREATMENT");
        }
        return out;
    }
}
