package zw.gov.mohcc.impilo.oros.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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

/**
 * OROS's only route into the Shared Health Record: fhir-gateway
 * {@code POST /internal/v1/gateway/forward} → BUTANO.
 *
 * <p>OROS previously posted lab results, imaging studies and documents straight at
 * {@code butano-service/fhir/*}. Those writes reached the record without passing a consent PEP and
 * left no row in {@code fhir_audit_log} — so nothing could answer "was this patient's result filed
 * against a valid legal basis" or even "was it filed at all". The gateway is where consent is
 * evaluated, the delivery target is resolved, and the decision is recorded; going round it is not
 * a shortcut but an ungoverned write.</p>
 *
 * <p>There is deliberately <b>no direct-to-BUTANO fallback</b>. If the gateway is unreachable the
 * write does not happen and the caller is told so. An ungoverned write is worse than a missing one:
 * a gap is visible and recoverable, a bypass is neither.</p>
 *
 * <p>Honesty law: only the gateway's own {@code outcome=SUCCESS} counts as written. Everything else
 * — a consent refusal, a downstream rejection, an outage — is NOT-written, and the distinction
 * between them is preserved rather than collapsed into a shrug.</p>
 */
@Service
public class FhirGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(FhirGatewayClient.class);

    public enum Outcome { SUCCESS, NO_ROUTE, FORWARD_FAILED, CONSENT_DENIED, UNREACHABLE }

    /**
     * @param resourceId the logical id the SHR assigned — the value callers persist as
     *                   {@code butanoRef}. Non-null only when {@code outcome} is SUCCESS <em>and</em>
     *                   the destination reported one; a successful write whose id was not returned
     *                   is still a successful write.
     * @param auditRef   the gateway's audit-log id: the durable receipt for this decision.
     */
    public record Result(Outcome outcome, String resourceId, String auditRef, String detail) {

        public boolean written() {
            return outcome == Outcome.SUCCESS;
        }

        static Result unreachable(String detail) {
            return new Result(Outcome.UNREACHABLE, null, null, detail);
        }
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public FhirGatewayClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                             @Value("${oros.integration.fhir-gateway.base-url:http://fhir-gateway-service:8091}")
                             String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /**
     * Forward one FHIR resource through the governed gateway.
     *
     * @param resource       the resource as a plain map, serialised here
     * @param subjectCpid    the pseudonymous subject — the gateway's consent PEP cannot evaluate
     *                       without it, so a null subject is refused here rather than sent as an
     *                       unattributed write
     * @param trustHeaders   the caller's propagated trust context
     * @param tenantId       the tenant the write belongs to; null means no usable trust context
     * @param purposeOfUse   the purpose the consent decision is made against
     * @param idempotencyKey the resource's own business key, so a retry is recognised end to end
     */
    public Result forward(String resourceType, String operation, Map<String, Object> resource,
                          String subjectCpid, HttpHeaders trustHeaders, UUID tenantId,
                          String purposeOfUse, String idempotencyKey) {
        if (tenantId == null) {
            // The gateway resolves routes and consent per tenant. Guessing one would file a
            // clinical resource under a tenancy nobody chose.
            log.warn("OROS→SHR: no tenant in the trust context; {} {} is not written",
                    operation, resourceType);
            return Result.unreachable("no tenant in trust context");
        }
        if (subjectCpid == null || subjectCpid.isBlank()) {
            log.warn("OROS→SHR: no subject CPID for {} {}; not written — an unattributed clinical "
                    + "resource cannot have consent evaluated against it", operation, resourceType);
            return Result.unreachable("no subject CPID");
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(resource);
        } catch (Exception e) {
            log.warn("OROS→SHR: could not serialise {} {}: {}", operation, resourceType, e.toString());
            return Result.unreachable("payload not serialisable");
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tenantId", tenantId.toString());
        request.put("correlationId", trustHeaders.getFirst("X-Correlation-ID"));
        request.put("actorId", trustHeaders.getFirst("X-Actor-ID"));
        request.put("sourceIp", null);
        request.put("resourceType", resourceType);
        request.put("operation", operation);
        request.put("payload", payload);
        request.put("subjectCpid", subjectCpid);
        request.put("purposeOfUse", purposeOfUse);

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(trustHeaders);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotency-Key", idempotencyKey);
        }

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/internal/v1/gateway/forward",
                    new HttpEntity<>(request, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Result.unreachable("fhir-gateway HTTP " + response.getStatusCode());
            }
            return parseEnvelope(response.getBody());
        } catch (RestClientException e) {
            log.warn("OROS→SHR: fhir-gateway unreachable for {} {}: {}",
                    operation, resourceType, e.getMessage());
            return Result.unreachable("fhir-gateway unreachable: " + e.getMessage());
        }
    }

    /** Envelope: {@code {"status":"ok","data":{outcome, auditLogId, downstreamResourceId, ...}}}. */
    private Result parseEnvelope(String body) {
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            String outcomeText = data.path("outcome").asText(null);
            if (outcomeText == null) {
                return Result.unreachable("fhir-gateway envelope missing outcome");
            }
            Outcome outcome = switch (outcomeText) {
                case "SUCCESS" -> Outcome.SUCCESS;
                case "NO_ROUTE" -> Outcome.NO_ROUTE;
                case "FORWARD_FAILED" -> Outcome.FORWARD_FAILED;
                case "CONSENT_DENIED" -> Outcome.CONSENT_DENIED;
                default -> Outcome.UNREACHABLE;
            };
            String resourceId = outcome == Outcome.SUCCESS
                    ? textOrNull(data, "downstreamResourceId") : null;
            String auditRef = data.hasNonNull("auditLogId") ? data.get("auditLogId").asText() : null;
            String detail = switch (outcome) {
                case CONSENT_DENIED -> "consent=" + data.path("consentOutcome").asText("?");
                case FORWARD_FAILED -> "downstream=" + data.path("downstreamStatus").asText("none");
                default -> outcomeText;
            };
            return new Result(outcome, resourceId, auditRef, detail);
        } catch (Exception e) {
            return Result.unreachable("unparseable fhir-gateway envelope: " + e.getMessage());
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
