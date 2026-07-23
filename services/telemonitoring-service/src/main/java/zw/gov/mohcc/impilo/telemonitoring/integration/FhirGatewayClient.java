package zw.gov.mohcc.impilo.telemonitoring.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The single designated SHR write seam (OF-B25, §14.5 step 14): telemonitoring →
 * fhir-gateway {@code POST /internal/v1/gateway/forward} → BUTANO. This is the ONLY
 * path by which monitoring-band Observations reach the record — closing the R72
 * three-writer sprawl. CPID-only subject; the gateway runs consent PEP → route lookup →
 * real downstream delivery → audit + outbox (the experience-bff forward idiom).
 *
 * <p>Headers are the service-originated v1.1 set ({@link TrustHeaderPropagation}) plus
 * {@code Idempotency-Key} = the reading's dedup key — the second half of journey #64's
 * double-write defence: a retried forward carries the same key end to end.</p>
 *
 * <p>Honesty law: only the gateway's own {@code outcome=SUCCESS} maps to
 * {@link Outcome#SUCCESS}. Transport failures and non-2xx responses map to
 * {@link Outcome#UNREACHABLE} — the caller must treat anything but SUCCESS as
 * NOT-written.</p>
 */
@Service
public class FhirGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(FhirGatewayClient.class);

    public enum Outcome { SUCCESS, NO_ROUTE, FORWARD_FAILED, CONSENT_DENIED, UNREACHABLE }

    /** {@code auditRef} is the gateway's audit-log id (its durable receipt), set only when known. */
    public record ForwardResult(Outcome outcome, String auditRef, String detail) {

        public static ForwardResult unreachable(String detail) {
            return new ForwardResult(Outcome.UNREACHABLE, null, detail);
        }
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public FhirGatewayClient(
            ObjectMapper objectMapper,
            @Value("${telemonitoring.integration.fhir-gateway-url:http://fhir-gateway-service:8091}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/$", ""))
                .build();
    }

    /**
     * Forward one FHIR resource through the governed gateway.
     *
     * @param resourceJson   the FHIR resource serialised as a JSON string (the ForwardRequest
     *                       {@code payload} contract)
     * @param idempotencyKey the reading dedup key — carried as the Idempotency-Key header
     */
    public ForwardResult forwardResource(UUID tenantId, String resourceType, String operation,
                                         String resourceJson, String subjectCpid,
                                         String purposeOfUse, String idempotencyKey) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tenantId", tenantId.toString());
        request.put("correlationId", UUID.randomUUID().toString());
        request.put("actorId", "telemonitoring-service");
        request.put("sourceIp", null);
        request.put("resourceType", resourceType);
        request.put("operation", operation);
        request.put("payload", resourceJson);
        request.put("subjectCpid", subjectCpid);
        request.put("purposeOfUse", purposeOfUse);
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri("/internal/v1/gateway/forward")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> applyHeaders(h, tenantId, idempotencyKey))
                    .body(request)
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return ForwardResult.unreachable("fhir-gateway HTTP " + response.getStatusCode());
            }
            return parseEnvelope(response.getBody());
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("fhir-gateway rejected forward ({} {}): HTTP {}", operation, resourceType, e.getStatusCode());
            return ForwardResult.unreachable("fhir-gateway HTTP " + e.getStatusCode());
        } catch (Exception e) {
            log.warn("fhir-gateway unreachable forwarding {} {}: {}", operation, resourceType, e.getMessage());
            return ForwardResult.unreachable("fhir-gateway unreachable: " + e.getMessage());
        }
    }

    private static void applyHeaders(HttpHeaders headers, UUID tenantId, String idempotencyKey) {
        TrustHeaderPropagation.applyServiceOriginated(headers, tenantId);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
    }

    /** Gateway envelope: {@code {"status":"ok","data":{outcome, auditLogId, consentOutcome, ...}}}. */
    private ForwardResult parseEnvelope(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            String outcomeText = data.path("outcome").asText(null);
            if (outcomeText == null) {
                return ForwardResult.unreachable("fhir-gateway envelope missing outcome");
            }
            Outcome outcome = switch (outcomeText) {
                case "SUCCESS" -> Outcome.SUCCESS;
                case "NO_ROUTE" -> Outcome.NO_ROUTE;
                case "FORWARD_FAILED" -> Outcome.FORWARD_FAILED;
                case "CONSENT_DENIED" -> Outcome.CONSENT_DENIED;
                default -> Outcome.UNREACHABLE;
            };
            String auditRef = data.hasNonNull("auditLogId")
                    ? "fhir-gateway:audit:" + data.get("auditLogId").asText() : null;
            String detail = outcome == Outcome.CONSENT_DENIED
                    ? "consent=" + data.path("consentOutcome").asText(null) : outcomeText;
            return new ForwardResult(outcome, auditRef, detail);
        } catch (Exception e) {
            return ForwardResult.unreachable("unparseable fhir-gateway envelope: " + e.getMessage());
        }
    }
}
