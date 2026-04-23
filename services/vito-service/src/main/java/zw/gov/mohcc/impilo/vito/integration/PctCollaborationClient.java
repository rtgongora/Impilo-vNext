package zw.gov.mohcc.impilo.vito.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Creates PCT clinical notes from governed patient-share contributions (provenance headers).
 */
@Component
public class PctCollaborationClient {

    private static final Logger log = LoggerFactory.getLogger(PctCollaborationClient.class);

    private static final String H_GRANT = "X-Patient-Share-Grant-Id";
    private static final String H_CONTRIBUTION = "X-Vito-Contribution-Id";
    private static final String H_TEMP_PROVIDER = "X-Temporary-Provider-Public-Id";
    private static final String H_SHARE_CORRELATION = "X-Patient-Share-Correlation-Id";
    private static final String H_TRUST_LEVEL = "X-External-Provider-Trust-Level";

    private final RestTemplate restTemplate = new RestTemplate();
    private final String pctBaseUrl;
    private final String bearerToken;

    public PctCollaborationClient(
            @Value("${vito.patient-share.pct-base-url:http://localhost:8088}") String pctBaseUrl,
            @Value("${vito.patient-share.pct-bearer-token:}") String bearerToken) {
        this.pctBaseUrl = trimSlash(pctBaseUrl);
        this.bearerToken = bearerToken == null ? "" : bearerToken.trim();
    }

    /**
     * POST /v1/clinical-notes with trust + patient-share provenance headers.
     *
     * @return parsed JSON map (PCT {@code ApiResponse} data envelope), or empty map on unexpected body
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createClinicalNote(
            TrustContext ctx,
            long grantId,
            long contributionId,
            String temporaryProviderPublicId,
            String shareCorrelationId,
            String trustLevelAtAuthoring,
            UUID patientHealthId,
            String noteBody,
            String authorLabel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patient_id", patientHealthId.toString());
        body.put("patient_health_id", patientHealthId.toString());
        body.put("author_id", temporaryProviderPublicId != null && !temporaryProviderPublicId.isBlank()
                ? temporaryProviderPublicId
                : "external-patient-share");
        body.put("author_name", authorLabel != null && !authorLabel.isBlank() ? authorLabel : "External collaborator");
        body.put("note_type", "EXTERNAL_COLLABORATION");
        body.put("body", noteBody != null ? noteBody : "");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (ctx != null && ctx.tenantId() != null) {
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
        }
        if (ctx != null) {
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.actorId() != null && !ctx.actorId().isBlank()) {
                headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            }
            if (ctx.actorType() != null && !ctx.actorType().isBlank()) {
                headers.set(TrustContext.H_ACTOR_TYPE, ctx.actorType());
            }
            if (ctx.purposeOfUse() != null && !ctx.purposeOfUse().isBlank()) {
                headers.set(TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse());
            }
        }
        if (!bearerToken.isBlank()) {
            headers.setBearerAuth(bearerToken);
        }
        headers.set(H_GRANT, String.valueOf(grantId));
        headers.set(H_CONTRIBUTION, String.valueOf(contributionId));
        if (temporaryProviderPublicId != null && !temporaryProviderPublicId.isBlank()) {
            headers.set(H_TEMP_PROVIDER, temporaryProviderPublicId);
        }
        if (shareCorrelationId != null && !shareCorrelationId.isBlank()) {
            headers.set(H_SHARE_CORRELATION, shareCorrelationId);
        }
        if (trustLevelAtAuthoring != null && !trustLevelAtAuthoring.isBlank()) {
            headers.set(H_TRUST_LEVEL, trustLevelAtAuthoring);
        }

        String url = pctBaseUrl + "/v1/clinical-notes";
        try {
            Map<?, ?> res = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> out = new LinkedHashMap<>();
            if (res != null) {
                Object data = res.get("data");
                if (data instanceof Map<?, ?> m) {
                    m.forEach((k, v) -> out.put(String.valueOf(k), v));
                    return out;
                }
                res.forEach((k, v) -> out.put(String.valueOf(k), v));
            }
            return out;
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 401 && bearerToken.isBlank()) {
                log.warn(
                        "PCT clinical note create returned 401 (configure vito.patient-share.pct-bearer-token for service auth): {}",
                        ex.getMessage());
            } else {
                log.warn("PCT clinical note create failed: {} {}", ex.getStatusCode(), ex.getMessage());
            }
            throw ex;
        } catch (Exception ex) {
            log.warn("PCT clinical note create failed: {}", ex.getMessage());
            throw new IllegalStateException("PCT collaboration call failed", ex);
        }
    }

    private static String trimSlash(String base) {
        if (base == null || base.isBlank()) {
            return "http://localhost:8088";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
