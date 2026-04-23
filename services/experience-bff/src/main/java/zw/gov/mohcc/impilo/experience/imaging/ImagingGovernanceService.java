package zw.gov.mohcc.impilo.experience.imaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tshepo PDP + audit ingest orchestration for governed imaging BFF routes.
 */
@Service
public class ImagingGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(ImagingGovernanceService.class);

    private final ImagingAccessPolicyService imagingAccessPolicyService;
    private final TshepoAuthzServiceClient tshepoAuthzServiceClient;
    private final TshepoAuditServiceClient tshepoAuditServiceClient;

    @Value("${impilo.security.allow-anonymous:false}")
    private boolean allowAnonymous;

    @Value("${impilo.imaging.require-tshepo-authorize:false}")
    private boolean requireTshepoAuthorize;

    @Value("${impilo.imaging.tshepo-pdp-fallback-allow:true}")
    private boolean tshepoPdpFallbackAllow;

    @Value("${impilo.imaging.audit-ingest-enabled:true}")
    private boolean auditIngestEnabled;

    public ImagingGovernanceService(
            ImagingAccessPolicyService imagingAccessPolicyService,
            TshepoAuthzServiceClient tshepoAuthzServiceClient,
            TshepoAuditServiceClient tshepoAuditServiceClient) {
        this.imagingAccessPolicyService = imagingAccessPolicyService;
        this.tshepoAuthzServiceClient = tshepoAuthzServiceClient;
        this.tshepoAuditServiceClient = tshepoAuditServiceClient;
    }

    public void assertGovernedRead() {
        imagingAccessPolicyService.requireClinicalImagingActor();
        if (allowAnonymous || !requireTshepoAuthorize) {
            return;
        }
        boolean allowed = tshepoAuthzServiceClient.imagingGovernedReadAllowed();
        if (!allowed && !tshepoPdpFallbackAllow) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tshepo PDP denied governed imaging read");
        }
        if (!allowed) {
            log.warn("Tshepo PDP denied governed imaging read — proceeding due to tshepo-pdp-fallback-allow=true");
        }
    }

    public void assertGovernedMutate() {
        imagingAccessPolicyService.requireClinicalImagingActor();
        if (allowAnonymous || !requireTshepoAuthorize) {
            return;
        }
        boolean allowed = tshepoAuthzServiceClient.imagingGovernedMutateAllowed();
        if (!allowed && !tshepoPdpFallbackAllow) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tshepo PDP denied governed imaging mutation");
        }
        if (!allowed) {
            log.warn("Tshepo PDP denied governed imaging mutation — proceeding due to tshepo-pdp-fallback-allow=true");
        }
    }

    public void assertViewerLaunch() {
        imagingAccessPolicyService.requireClinicalImagingActor();
        if (allowAnonymous || !requireTshepoAuthorize) {
            return;
        }
        boolean allowed = tshepoAuthzServiceClient.imagingViewerLaunchAllowed();
        if (!allowed && !tshepoPdpFallbackAllow) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tshepo PDP denied imaging viewer launch");
        }
        if (!allowed) {
            log.warn("Tshepo PDP denied imaging viewer launch — proceeding due to tshepo-pdp-fallback-allow=true");
        }
    }

    /**
     * Records a clinical imaging audit row in TSHEPO Audit (best-effort).
     */
    public void recordImagingAudit(
            String tenantIdHeader,
            String correlationIdHeader,
            String purposeOfUseHeader,
            String facilityIdHeader,
            String eventType,
            String action,
            String outcome,
            String actorId,
            String actorType,
            String subjectRef,
            String resourceType,
            String resourceId,
            Map<String, Object> detail) {
        if (!auditIngestEnabled || allowAnonymous) {
            return;
        }
        UUID tenantId = parseTenantUuid(tenantIdHeader);
        UUID correlationId = parseCorrelationUuid(correlationIdHeader);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("eventType", eventType);
        body.put("actorId", actorId != null ? actorId : "unknown");
        body.put("actorType", actorType != null ? actorType : "UNKNOWN");
        body.put("subjectRef", subjectRef);
        body.put("resourceType", resourceType);
        body.put("resourceId", resourceId);
        body.put("action", action);
        body.put("outcome", outcome);
        body.put("purposeOfUse", purposeOfUseHeader != null && !purposeOfUseHeader.isBlank() ? purposeOfUseHeader : "TREATMENT");
        if (facilityIdHeader != null && !facilityIdHeader.isBlank()) {
            try {
                body.put("facilityId", UUID.fromString(facilityIdHeader.trim()).toString());
            } catch (IllegalArgumentException ignored) {
                // non-UUID facility keys are ignored for audit ingest schema
            }
        }
        body.put("correlationId", correlationId.toString());
        if (detail != null && !detail.isEmpty()) {
            body.put("detail", detail);
        }

        tshepoAuditServiceClient.ingestAuditEvent(body);
    }

    public static UUID parseTenantUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return new UUID(0L, 0L);
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(raw.trim().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static UUID parseCorrelationUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(raw.trim().getBytes(StandardCharsets.UTF_8));
        }
    }
}
