package zw.gov.mohcc.impilo.msikaflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * OROS clinical-order plane client (OF-B4 read seam + OF-B11 step-6 claim seam).
 *
 * <p>RFO requests pin an immutable {@code orderVersionId} (read-only reference —
 * never copying clinical truth beyond the §11.8 allow-list); commitment step 6
 * claims the prescription token atomically and step-6 failure compensation
 * releases the claim. Header idiom follows {@link NhumeClient}/{@link MushexClient}
 * (v1.1 trust headers forwarded from the inbound servlet request).</p>
 */
@Service
public class OrosClient {

    private static final Logger log = LoggerFactory.getLogger(OrosClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public record PrescriptionClaim(String claimId, String prescriptionVersionId, boolean duplicateReplay) {}

    public OrosClient(ObjectMapper objectMapper,
                      @Value("${msika-flow.integration.oros-url:http://oros-service:8089}") String orosBaseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(orosBaseUrl.replaceAll("/$", ""))
                .build();
    }

    /**
     * GET /v1/orders/{orderId}/versions — verifies the RFO's version pin exists.
     *
     * @return {@code Optional.of(true/false)} when OROS answered (version present
     *         or not), {@code Optional.empty()} on transport failure so callers
     *         FAIL CLOSED (an unreachable order plane never self-validates a pin).
     */
    public Optional<Boolean> orderVersionExists(String orderId, String orderVersionId, HttpServletRequest inbound) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri("/v1/orders/{orderId}/versions", orderId)
                    .headers(h -> copyTrustHeaders(inbound, h, "marketplace-request:" + orderId))
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("OROS versions lookup failed for order {}: HTTP {}", orderId, response.getStatusCode());
                return Optional.empty();
            }
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            if (!data.isArray()) {
                return Optional.empty();
            }
            for (JsonNode version : data) {
                if (orderVersionId.equals(version.path("orderVersionId").asText())) {
                    return Optional.of(Boolean.TRUE);
                }
            }
            return Optional.of(Boolean.FALSE);
        } catch (Exception e) {
            log.warn("OROS versions lookup failed for order {}: {}", orderId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * POST /v1/prescriptions/claims — commitment step 6 (§8.9): atomic,
     * version-bound token claim; server decrements the repeats counter in the
     * same transaction. Returns empty on any failure (RC-7 — the caller fails
     * the commitment and compensates, never fakes a claim).
     */
    public Optional<PrescriptionClaim> claimPrescription(String token, String vendorRef,
                                                         String idempotencyKey, HttpServletRequest inbound) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("token", token);
            if (vendorRef != null) {
                body.put("vendorRef", vendorRef);
            }
            ResponseEntity<String> response = restClient.post()
                    .uri("/v1/prescriptions/claims")
                    .headers(h -> {
                        copyTrustHeaders(inbound, h, idempotencyKey);
                        h.set("Idempotency-Key", idempotencyKey);
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("OROS prescription claim failed: HTTP {}", response.getStatusCode());
                return Optional.empty();
            }
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            String claimId = data.path("claimId").asText(null);
            if (claimId == null || claimId.isBlank()) {
                log.warn("OROS prescription claim response missing claimId");
                return Optional.empty();
            }
            return Optional.of(new PrescriptionClaim(claimId,
                    data.path("prescriptionVersionId").asText(null),
                    data.path("duplicateReplay").asBoolean(false)));
        } catch (Exception e) {
            log.warn("OROS prescription claim failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * POST /v1/prescriptions/claims/{claimId}/release — RC-7/RC-5 compensation:
     * restore the repeats counter when a later commitment step fails.
     *
     * @return true when the release was acknowledged; false is surfaced (logged,
     *         recorded in the step log) — never swallowed as success.
     */
    public boolean releaseClaim(String claimId, String reason, HttpServletRequest inbound) {
        try {
            Map<String, Object> body = Map.of("reason", reason != null ? reason : "COMMITMENT_COMPENSATION");
            ResponseEntity<String> response = restClient.post()
                    .uri("/v1/prescriptions/claims/{claimId}/release", claimId)
                    .headers(h -> copyTrustHeaders(inbound, h, "claim-release:" + claimId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("OROS claim release FAILED for claim {} — manual compensation required: {}", claimId, e.getMessage());
            return false;
        }
    }

    private static void copyTrustHeaders(HttpServletRequest inbound, HttpHeaders target, String idempotencySeed) {
        if (inbound != null) {
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_TENANT_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_TYPE);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_PURPOSE_OF_USE);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_DEVICE_FINGERPRINT);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_CORRELATION_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_FACILITY_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_WORKSPACE_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_SHIFT_ID);
            copyIfPresent(inbound, target, "Authorization");
        }
        String pod = inbound != null ? inbound.getHeader("X-Pod-ID") : null;
        target.add("X-Pod-ID", pod != null && !pod.isBlank() ? pod : "national-spine");
        target.add("X-Request-ID", java.util.UUID.randomUUID().toString());
        target.add("X-Idempotency-Key", "msika-flow:" + idempotencySeed);
        target.add("x-envoy-internal", "true");
    }

    private static void copyIfPresent(HttpServletRequest inbound, HttpHeaders target, String name) {
        String v = inbound.getHeader(name);
        if (v != null && !v.isBlank()) {
            target.add(name, v);
        }
    }
}
