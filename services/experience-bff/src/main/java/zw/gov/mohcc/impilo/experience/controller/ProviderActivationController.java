package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider Activation BFF Controller — bridges the experience shell to VARAPI
 * for listing Provider IDs linked to a person's Health ID.
 *
 * <p>Implements the Health OS doctrine's Identity and Role Activation model:
 * a person signs in with their Health ID (who they are), then activates a
 * Provider ID to practice in a regulated professional capacity. Professional
 * execution requires a valid Provider ID with current licensure, organizational
 * affiliation, facility context, and declared purpose of use.</p>
 *
 * <p>This controller supports the role activation flow by retrieving all provider
 * identities attached to a given Health ID, enabling the experience shell to
 * present the "Practice as..." selector when a person has provider credentials.</p>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>GET /internal/v1/identity/providers?actorId={healthId} — list Provider IDs for a person</li>
 * </ul>
 *
 * @see <a href="docs/doctrine/health-os-doctrine.md">Health OS Doctrine — Identity and Role Activation</a>
 */
@RestController
@RequestMapping("/internal/v1/identity/providers")
public class ProviderActivationController {

    private static final Logger log = LoggerFactory.getLogger(ProviderActivationController.class);

    private final VarapiServiceClient varapiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProviderActivationController(VarapiServiceClient varapiClient) {
        this.varapiClient = varapiClient;
    }

    /**
     * List all Provider IDs linked to a person's Health ID.
     *
     * <p>Queries VARAPI to retrieve provider identities associated with the given
     * actor (Health ID). Each result includes the provider's display name, cadre,
     * registration number, status, and licensure expiry — everything the experience
     * shell needs to render the role activation selector and enforce graduated friction.</p>
     *
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @param actorId       the Health ID of the person whose provider identities are requested
     * @return list of provider identity objects with cadre, licensure, and activation status
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listProvidersByActor(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String actorId) {

        log.info("Listing providers for actor: tenant={}, actorId={}, requestId={}",
                tenantId, actorId, requestId);

        try {
            JsonNode result = varapiClient.getProviderByHealthId(actorId);
            if (result != null) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("data", result);
                response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "PROVIDER_NOT_FOUND", "message", "No provider profile found for actor"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("VARAPI lookup failed for actorId={}: {}",
                    actorId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "VARAPI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * Provider biometric step-up on Provider-ID activation (L2).
     *
     * <p>The experience shell captures an optional biometric probe on the activation screen.
     * VARAPI keys provider biometrics by the provider public id, so the subjectRef here is the
     * {@code providerId} path segment. The BFF normalises VARAPI's outcome into a single, stable,
     * always-200 contract so the shell has one unambiguous signal to act on:</p>
     *
     * <ul>
     *   <li>{@code MATCH} — the shell may proceed and mark the activation biometric-verified.</li>
     *   <li>{@code NO_MATCH} — an explicit identity mismatch; the shell blocks activation. This is
     *       the ONLY outcome that blocks.</li>
     *   <li>{@code UNAVAILABLE} — no enrolment, policy withheld, an upstream error, or a biometric
     *       outage. Care-first: the shell falls back to the existing activation factor and never
     *       blocks on a biometric outage. Anything that is not an explicit identity decision
     *       collapses here — an error is never mistaken for a NO_MATCH.</li>
     * </ul>
     *
     * <p>Request body: {@code {modality: FINGERPRINT|FACE|IRIS, probeBase64}}.</p>
     */
    @PostMapping(value = "/{providerId}/biometric-verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> biometricVerify(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String providerId,
            @RequestBody Map<String, Object> body) {

        Object modality = body.get("modality");
        Object probe = body.containsKey("probeBase64") ? body.get("probeBase64") : body.get("templateDataBase64");

        Map<String, Object> upstream = new LinkedHashMap<>();
        upstream.put("modality", modality);
        upstream.put("templateDataBase64", probe);
        upstream.put("workflowType", "PROVIDER_ACTIVATION_STEP_UP");
        upstream.put("contextType", "FACILITY");
        upstream.put("biometricIntent", "PROVIDER_ACTIVATION");

        log.info("Provider biometric step-up verify: tenant={}, providerId={}, modality={}, requestId={}",
                tenantId, providerId, modality, requestId);

        try {
            JsonNode data = varapiClient.verifyProviderBiometric(providerId, upstream);
            return ResponseEntity.ok(normalise(data, requestId, correlationId));
        } catch (HttpStatusCodeException ex) {
            // VARAPI returned a 4xx/5xx. A genuine identity NO_MATCH arrives as a 200 body, so a
            // non-2xx normally means "could not verify" (no enrolment, policy withheld, upstream
            // error) — care-first UNAVAILABLE. Defensively, if the error body still carries an
            // explicit NO_MATCH we surface it honestly rather than failing open.
            String result = readResult(ex.getResponseBodyAsString());
            if ("NO_MATCH".equalsIgnoreCase(result)) {
                log.info("Provider biometric step-up: honest NO_MATCH surfaced from VARAPI error body (providerId={})",
                        providerId);
                return ResponseEntity.ok(outcome("NO_MATCH", null, null, requestId, correlationId));
            }
            log.info("Provider biometric step-up UNAVAILABLE (varapi status={}, providerId={}) — activation falls back",
                    ex.getStatusCode().value(), providerId);
            return ResponseEntity.ok(outcome("UNAVAILABLE", "BIOMETRIC_VERIFY_UNAVAILABLE", null, requestId, correlationId));
        } catch (Exception ex) {
            // Transport failure / biometric outage — never block activation on a biometric outage.
            log.warn("Provider biometric step-up outage (providerId={}): {} — activation falls back",
                    providerId, ex.getMessage());
            return ResponseEntity.ok(outcome("UNAVAILABLE", "BIOMETRIC_SERVICE_UNAVAILABLE", null, requestId, correlationId));
        }
    }

    /** Normalise a 2xx VARAPI verify body into the stable MATCH / NO_MATCH / UNAVAILABLE contract. */
    private Map<String, Object> normalise(JsonNode data, String requestId, String correlationId) {
        String raw = data != null && data.hasNonNull("result") ? data.get("result").asText() : "UNAVAILABLE";
        String result = switch (raw == null ? "" : raw.toUpperCase()) {
            case "MATCH" -> "MATCH";
            case "NO_MATCH" -> "NO_MATCH";
            default -> "UNAVAILABLE"; // NO_REFERENCE, unknown, blank — anything not an explicit decision
        };
        Double confidence = data != null && data.hasNonNull("confidence") ? data.get("confidence").asDouble() : null;
        Long eventId = data != null && data.hasNonNull("verificationEventId")
                ? data.get("verificationEventId").asLong() : null;
        return outcome(result, null, confidence == null && eventId == null ? null : new Object[]{confidence, eventId},
                requestId, correlationId);
    }

    private Map<String, Object> outcome(String result, String reason, Object extras,
                                        String requestId, String correlationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("result", result);
        if (reason != null) out.put("reason", reason);
        if (extras instanceof Object[] arr) {
            if (arr.length > 0 && arr[0] != null) out.put("confidence", arr[0]);
            if (arr.length > 1 && arr[1] != null) out.put("verificationEventId", arr[1]);
        }
        out.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return out;
    }

    private String readResult(String errorBody) {
        if (errorBody == null || errorBody.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(errorBody);
            JsonNode data = node.has("data") ? node.get("data") : node;
            return data != null && data.hasNonNull("result") ? data.get("result").asText() : null;
        } catch (Exception parseError) {
            return null;
        }
    }
}
