package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Calls {@code tshepo-consent-service} to evaluate consent for a clinical access decision.
 *
 * <h2>The wire contract this had to be corrected to</h2>
 *
 * <p>This client used to POST a JSON body and expect a bare {@link ConsentDecision}. The producer
 * ({@code ConsentEvaluationController}) exposes only {@code GET /v1/consent/evaluate} with query
 * parameters, returning an {@code ApiResponse} envelope. Five mismatches at once:</p>
 *
 * <ol>
 *   <li><b>Method</b> — POST against a GET-only mapping ⇒ 405.</li>
 *   <li><b>Subject</b> — sent {@code resourceId}, plus a {@code resourceType} the producer does not
 *       know; the producer requires {@code subjectRef}.</li>
 *   <li><b>Purpose</b> — sent {@code purposeOfUse}; the producer requires {@code purpose}.</li>
 *   <li><b>Scope</b> — the producer requires {@code scope} and it was never sent, so even a correct
 *       method would have been rejected as a missing parameter.</li>
 *   <li><b>Envelope</b> — expected a bare decision; the producer wraps it, so a "successful" call
 *       would have deserialised to all-nulls, i.e. {@code permitted=false}.</li>
 * </ol>
 *
 * <p>The catch-all below turns every failure into {@code deny("CONSENT_SERVICE_UNAVAILABLE")}, so
 * the break was invisible: fail-closed hid a call that could never have succeeded. It stayed latent
 * only because Envoy/ext_authz is off the live path. The moment the PDP goes on-path, every
 * consent-gated request would have been denied — and the cause would have read as a consent
 * problem rather than a wire problem.</p>
 *
 * <p>Fail-closed on error is retained deliberately. A consent service that cannot be reached must
 * not yield access; what changed is that a reachable one is now asked the right question.</p>
 */
@Service
public class ConsentClient {

    private static final Logger log = LoggerFactory.getLogger(ConsentClient.class);

    /**
     * The producer requires a scope and defines no default. Read access is the correct question for
     * an authorization check — the PDP is asking "may this actor see this subject's data", and a
     * broader scope would ask something the decision does not need.
     */
    static final String DEFAULT_SCOPE = "read";

    private final RestTemplate restTemplate;
    private final AuthzProperties properties;

    public ConsentClient(RestTemplate restTemplate, AuthzProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * @param tenantId     tenant isolation
     * @param resourceType FHIR resource type — used for logging only. Deliberately NOT sent: the
     *                     producer's contract has no such parameter, and sending it was part of
     *                     the original mismatch
     * @param subjectRef   the subject whose consent governs this access (CPID for patients)
     * @param actorId      the requesting actor
     * @param purpose      declared purpose of use
     */
    public ConsentDecision evaluateConsent(UUID tenantId, String resourceType,
                                            String subjectRef, String actorId,
                                            String purpose) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(properties.getConsentServiceUrl() + properties.getConsentEvaluatePath())
                    .queryParam("tenantId", tenantId)
                    .queryParam("actorId", actorId)
                    .queryParam("subjectRef", subjectRef)
                    .queryParam("purpose", purpose)
                    .queryParam("scope", DEFAULT_SCOPE)
                    .toUriString();

            // Read the envelope as a tree rather than binding ApiResponse<ConsentDecision>: generic
            // erasure makes the typed form silently yield a LinkedHashMap for `data`.
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            JsonNode body = response.getBody();
            if (body == null) {
                log.warn("Consent service returned an empty body for subject={} actor={}", subjectRef, actorId);
                return ConsentDecision.deny("CONSENT_RESPONSE_EMPTY");
            }

            JsonNode data = body.path("data");
            if (data.isMissingNode() || data.isNull()) {
                // An envelope carrying an error rather than data. Treating that as "no consent"
                // would silently convert a producer fault into a clean-looking denial.
                log.warn("Consent service returned no data node for subject={} actor={}: {}",
                        subjectRef, actorId, body.path("error").path("code").asText("unknown"));
                return ConsentDecision.deny("CONSENT_RESPONSE_MALFORMED");
            }

            return new ConsentDecision(
                    data.path("permitted").asBoolean(false),
                    asTextOrNull(data.path("consentId")),
                    asTextOrNull(data.path("provision")),
                    textList(data.path("allowedScopes")),
                    textList(data.path("deniedScopes")),
                    asTextOrNull(data.path("reason")));

        } catch (Exception e) {
            log.error("Consent evaluation failed for resourceType={} subject={} actor={}: {}",
                    resourceType, subjectRef, actorId, e.getMessage());
            // Fail-closed: an unreachable consent service must never yield access.
            return ConsentDecision.deny("CONSENT_SERVICE_UNAVAILABLE");
        }
    }

    private static String asTextOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static List<String> textList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> {
                String v = n.asText();
                if (v != null && !v.isBlank()) {
                    out.add(v);
                }
            });
        }
        return out;
    }
}
