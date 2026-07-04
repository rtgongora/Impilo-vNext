package zw.gov.mohcc.impilo.costa.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Calls coverage-service (member coverage, eligibility, claims) forwarding
 * trust headers from the inbound servlet request — same pattern as
 * {@link MushexPaymentIntentClient}. coverage-service remains the SoR for
 * plans/members/claims; COSTA only consumes eligibility and files claims.
 */
@Component
public class CoverageServiceClient {

    private final RestClient coverageRestClient;
    private final ObjectMapper objectMapper;

    /** Active member coverage joined to its plan. */
    public record MemberCoverage(UUID coverageId, String planCode, String status) {}

    /** Eligibility verdict for a coverage. */
    public record Eligibility(String resultCode, String resultMessage) {
        public boolean eligible() { return "ELIGIBLE".equalsIgnoreCase(resultCode); }
    }

    public CoverageServiceClient(@Qualifier("coverageRestClient") RestClient coverageRestClient,
                                 ObjectMapper objectMapper) {
        this.coverageRestClient = coverageRestClient;
        this.objectMapper = objectMapper;
    }

    /** First ACTIVE coverage for the client, or null when the member has none. */
    public MemberCoverage findActiveMemberCoverage(HttpServletRequest inbound, String clientId) {
        ResponseEntity<String> response = coverageRestClient.get()
                .uri("/internal/v1/coverage/member/{clientId}", clientId)
                .headers(h -> copyTrustHeaders(inbound, h))
                .retrieve()
                .toEntity(String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Coverage member lookup failed: HTTP " + response.getStatusCode());
        }
        try {
            JsonNode rows = objectMapper.readTree(response.getBody());
            for (JsonNode row : rows) {
                if ("ACTIVE".equalsIgnoreCase(row.path("status").asText(""))) {
                    return new MemberCoverage(
                            UUID.fromString(row.path("id").asText()),
                            row.path("planCode").asText(null),
                            row.path("status").asText(null));
                }
            }
            return null;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Coverage member response parse failed", e);
        }
    }

    public Eligibility checkEligibility(HttpServletRequest inbound, UUID coverageId, String patientRef) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("coverageId", coverageId);
        body.put("patientRef", patientRef);

        ResponseEntity<String> response = coverageRestClient.post()
                .uri("/internal/v1/coverage/eligibility/check")
                .headers(h -> {
                    copyTrustHeaders(inbound, h);
                    h.set("Idempotency-Key", "costa-elig:" + coverageId + ":" + UUID.randomUUID());
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Coverage eligibility check failed: HTTP " + response.getStatusCode());
        }
        try {
            JsonNode node = objectMapper.readTree(response.getBody());
            return new Eligibility(node.path("resultCode").asText(""), node.path("resultMessage").asText(""));
        } catch (Exception e) {
            throw new IllegalStateException("Coverage eligibility response parse failed", e);
        }
    }

    /** Files a claim at the coverage SoR; returns the cv_claim id. */
    public UUID submitClaim(HttpServletRequest inbound, UUID coverageId, String facilityId,
                            String providerId, String encounterId, String claimType,
                            String lineItemsJson, java.math.BigDecimal totalAmount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("coverageId", coverageId);
        body.put("facilityId", facilityId);
        body.put("providerId", providerId);
        body.put("encounterId", encounterId);
        body.put("claimType", claimType);
        body.put("lineItems", lineItemsJson);
        body.put("totalAmount", totalAmount);

        ResponseEntity<String> response = coverageRestClient.post()
                .uri("/internal/v1/coverage/claims")
                .headers(h -> {
                    copyTrustHeaders(inbound, h);
                    // Deterministic per encounter+coverage: a retried claim files once.
                    h.set("Idempotency-Key", "costa-claim:" + coverageId + ":" + encounterId);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Coverage claim submission failed: HTTP " + response.getStatusCode());
        }
        try {
            JsonNode node = objectMapper.readTree(response.getBody());
            return UUID.fromString(node.path("id").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Coverage claim response parse failed", e);
        }
    }

    private static void copyTrustHeaders(HttpServletRequest inbound, org.springframework.http.HttpHeaders target) {
        if (inbound == null) {
            return;
        }
        copyIfPresent(inbound, target, TrustContext.H_TENANT_ID);
        copyIfPresent(inbound, target, TrustContext.H_ACTOR_ID);
        copyIfPresent(inbound, target, TrustContext.H_ACTOR_TYPE);
        copyIfPresent(inbound, target, TrustContext.H_PURPOSE_OF_USE);
        copyIfPresent(inbound, target, TrustContext.H_CORRELATION_ID);
        copyIfPresent(inbound, target, TrustContext.H_FACILITY_ID);
        copyIfPresent(inbound, target, "X-Pod-ID");
        copyIfPresent(inbound, target, "X-Request-ID");
        copyIfPresent(inbound, target, "Authorization");
    }

    private static void copyIfPresent(HttpServletRequest inbound,
                                      org.springframework.http.HttpHeaders target,
                                      String header) {
        String value = inbound.getHeader(header);
        if (value != null && !value.isBlank()) {
            target.set(header, value);
        }
    }
}
