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
import java.util.LinkedHashMap;
import java.util.Map;
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
}
