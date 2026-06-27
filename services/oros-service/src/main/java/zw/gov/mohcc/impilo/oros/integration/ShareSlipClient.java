package zw.gov.mohcc.impilo.oros.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client for the generic secure-link primitive (share-slip-service) used to issue scoped,
 * OTP-protected, time-limited external access to diagnostic results
 * ({@code subjectType=DIAGNOSTIC_RESULT}).
 *
 * <p>Unlike best-effort writebacks, issuing a secure link is the explicit purpose of the call, so
 * a failure surfaces as an exception rather than degrading silently.</p>
 */
@Service
public class ShareSlipClient {

    private static final Logger log = LoggerFactory.getLogger(ShareSlipClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ShareSlipClient(RestTemplate restTemplate,
                           @Value("${oros.integration.share-slip.base-url:http://localhost:8104}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    /** A created secure link reference. */
    public record ShareLinkRef(String linkId, String shareToken, String status, String expiresAt) {}

    /** Result of claiming a share link (self-describing: carries the claimed subject). */
    public record ClaimRef(boolean success, String subjectType, String subjectId, String message) {}

    /**
     * Create a secure share link for a subject. Documents are optional: a document-less link (e.g.
     * a DIAGNOSTIC_ORDER QR) points to the subject itself. Callers that require documents (e.g. an
     * external result link) validate that upstream.
     *
     * @throws IllegalStateException if share-slip is not configured or the call fails
     */
    @SuppressWarnings("unchecked")
    public ShareLinkRef createLink(String subjectType, String subjectId, String subjectName,
                                   List<UUID> documentIds, String purpose,
                                   int expiryHours, int maxClaims) {
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("share-slip is not configured (no base URL)");
        }
        try {
            String url = baseUrl + "/v1/internal/share-links";
            Map<String, Object> body = new HashMap<>();
            body.put("subjectType", subjectType);
            body.put("subjectId", subjectId);
            body.put("subjectName", subjectName);
            body.put("documentIds", documentIds != null
                    ? documentIds.stream().map(UUID::toString).toList() : List.of());
            body.put("purpose", purpose);
            body.put("verificationMethod", "OTP");
            body.put("expiryHours", expiryHours);
            body.put("maxClaims", maxClaims);

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object dataObj = response.getBody().get("data");
                Map<String, Object> data = (dataObj instanceof Map) ? (Map<String, Object>) dataObj : response.getBody();
                return new ShareLinkRef(
                        str(data.get("linkId")), str(data.get("shareToken")),
                        str(data.get("status")), str(data.get("expiresAt")));
            }
            throw new IllegalStateException("share-slip returned " + response.getStatusCode());

        } catch (RestClientException e) {
            log.warn("share-slip unavailable for {} {}: {}", subjectType, subjectId, e.getMessage());
            throw new IllegalStateException("share-slip unavailable: " + e.getMessage(), e);
        }
    }

    /**
     * Claim a share link (public claim flow) with an OTP, returning the self-describing result so
     * the caller can resolve the claimed subject (e.g. the order behind a QR).
     *
     * @throws IllegalStateException if share-slip is not configured or unreachable
     */
    @SuppressWarnings("unchecked")
    public ClaimRef claim(String shareToken, String otp, String deviceFingerprint) {
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("share-slip is not configured (no base URL)");
        }
        try {
            String url = baseUrl + "/v1/public/share/claim";
            Map<String, Object> body = new HashMap<>();
            body.put("shareToken", shareToken);
            body.put("otp", otp);
            body.put("deviceFingerprint", deviceFingerprint);

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object dataObj = response.getBody().get("data");
                Map<String, Object> data = (dataObj instanceof Map) ? (Map<String, Object>) dataObj : response.getBody();
                boolean success = Boolean.TRUE.equals(data.get("success"));
                return new ClaimRef(success, str(data.get("subjectType")),
                        str(data.get("subjectId")), str(data.get("message")));
            }
            throw new IllegalStateException("share-slip returned " + response.getStatusCode());

        } catch (RestClientException e) {
            log.warn("share-slip unavailable for claim: {}", e.getMessage());
            throw new IllegalStateException("share-slip unavailable: " + e.getMessage(), e);
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.facilityId() != null) {
                headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            }
        } catch (IllegalStateException e) {
            log.debug("No trust context available for share-slip call headers");
        }
        return headers;
    }
}
