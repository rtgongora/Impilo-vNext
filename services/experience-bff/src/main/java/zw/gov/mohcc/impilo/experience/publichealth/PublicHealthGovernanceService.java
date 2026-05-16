package zw.gov.mohcc.impilo.experience.publichealth;

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
public class PublicHealthGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(PublicHealthGovernanceService.class);

    private final TshepoAuthzServiceClient tshepoAuthzServiceClient;
    private final TshepoAuditServiceClient tshepoAuditServiceClient;

    @Value("${impilo.security.allow-anonymous:false}")
    private boolean allowAnonymous;

    @Value("${impilo.public-health.require-tshepo-authorize:true}")
    private boolean requireTshepoAuthorize;

    @Value("${impilo.public-health.tshepo-pdp-fallback-allow:false}")
    private boolean tshepoPdpFallbackAllow;

    @Value("${impilo.public-health.audit-ingest-enabled:true}")
    private boolean auditIngestEnabled;

    public PublicHealthGovernanceService(
            TshepoAuthzServiceClient tshepoAuthzServiceClient,
            TshepoAuditServiceClient tshepoAuditServiceClient) {
        this.tshepoAuthzServiceClient = tshepoAuthzServiceClient;
        this.tshepoAuditServiceClient = tshepoAuditServiceClient;
    }

    public void assertGovernedRead() {
        enforce(tshepoAuthzServiceClient.publicHealthGovernedReadAllowed(), "Tshepo PDP denied public-health read");
    }

    public void assertGovernedMutate() {
        enforce(tshepoAuthzServiceClient.publicHealthGovernedMutateAllowed(), "Tshepo PDP denied public-health mutation");
    }

    public void assertGovernedExport() {
        enforce(tshepoAuthzServiceClient.publicHealthGovernedExportAllowed(), "Tshepo PDP denied public-health export");
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

    public void audit(
            String tenantId,
            String correlationId,
            String purposeOfUse,
            String facilityId,
            String eventType,
            String action,
            String outcome,
            String resourceType,
            String resourceId,
            Map<String, Object> detail) {
        if (!auditIngestEnabled || allowAnonymous) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", parseUuid(tenantId, false));
        payload.put("eventType", eventType);
        payload.put("actorId", "public-health-bff");
        payload.put("actorType", "SERVICE");
        payload.put("resourceType", resourceType);
        payload.put("resourceId", resourceId);
        payload.put("action", action);
        payload.put("outcome", outcome);
        payload.put("purposeOfUse", purposeOfUse == null || purposeOfUse.isBlank() ? "PUBLIC_HEALTH" : purposeOfUse);
        payload.put("occurredAt", OffsetDateTime.now().toString());
        payload.put("correlationId", parseUuid(correlationId, true).toString());
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
