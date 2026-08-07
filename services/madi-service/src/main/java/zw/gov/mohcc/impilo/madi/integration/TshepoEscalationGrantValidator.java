package zw.gov.mohcc.impilo.madi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.sharedkernel.security.EscalationGrantValidator;

import java.util.Map;

/**
 * Asks tshepo-authz whether an escalation grant holds.
 *
 * <h2>This class is where the ruling is actually implemented</h2>
 * <p>The guard has three branches, but the decision about <em>which</em> branch a request falls into
 * is made here, in the mapping from HTTP outcome to {@link Outcome}. Get this wrong and the guard's
 * careful three-way switch is decorative.</p>
 *
 * <p>The mapping, and why each line is what it is:</p>
 *
 * <pre>
 *   200 + {"outcome":"VALID"}     → VALID         the trust plane confirmed a grant
 *   200 + {"outcome":"NO_GRANT"}  → NO_GRANT      the trust plane answered, and the answer is no
 *   4xx                           → NO_GRANT      it answered; we asked badly, which is not its outage
 *   5xx / timeout / connect / DNS → UNREACHABLE   it did not answer
 *   200 + unparseable body        → UNREACHABLE   something answered, but not this service
 * </pre>
 *
 * <p><b>There is deliberately no catch-all returning NO_GRANT.</b> The single most likely way to
 * rebuild the defect this replaced is a {@code catch (Exception e) { return NO_GRANT; }} — it looks
 * conservative, reads as fail-closed, and would block an emergency transfusion every time
 * tshepo-authz restarts. The final catch here returns {@link Outcome#UNREACHABLE} and says why.</p>
 *
 * <p>The inverse mistake — treating an outage as VALID — is worse and is not reachable: VALID is
 * returned only on an explicit {@code "VALID"} string in a parsed 200 body.</p>
 *
 * <p>4xx maps to NO_GRANT rather than UNREACHABLE because the service <em>did</em> answer. A 400 or
 * 404 means our request was wrong or the grant is not there; neither is an outage, and treating a
 * malformed request as grounds for an ungoverned override would make the override branch reachable
 * by sending rubbish.</p>
 */
@Service
public class TshepoEscalationGrantValidator implements EscalationGrantValidator {

    private static final Logger log = LoggerFactory.getLogger(TshepoEscalationGrantValidator.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TshepoEscalationGrantValidator(
            RestTemplate restTemplate,
            @Value("${madi.integration.tshepo-authz.base-url:http://tshepo-authz-service:8081}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public Outcome validate(String tenantId, String actorId, String grantId) {
        if (tenantId == null || tenantId.isBlank() || actorId == null || actorId.isBlank()
                || grantId == null || grantId.isBlank()) {
            // Nothing to ask about. This is an answer, not an outage: a caller presenting no grant
            // has no grant.
            return Outcome.NO_GRANT;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-tenant-id", tenantId);
        headers.set("x-actor-id", actorId);

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    baseUrl + "/v1/visibility-escalations/grants/validate",
                    new HttpEntity<>(Map.of("grantId", grantId), headers),
                    JsonNode.class);

            JsonNode body = response.getBody();
            if (body == null || body.get("outcome") == null) {
                log.error("BREAK-GLASS: grant validation returned {} with an unusable body — treating "
                        + "as UNREACHABLE, not as a refusal.", response.getStatusCode());
                return Outcome.UNREACHABLE;
            }
            String outcome = body.get("outcome").asText();
            if ("VALID".equals(outcome)) {
                return Outcome.VALID;
            }
            if ("NO_GRANT".equals(outcome)) {
                return Outcome.NO_GRANT;
            }
            log.error("BREAK-GLASS: grant validation returned unknown outcome '{}' — treating as "
                    + "UNREACHABLE. Only an explicit VALID grants access.", outcome);
            return Outcome.UNREACHABLE;

        } catch (RestClientResponseException e) {
            // The service answered with a status. 4xx is an answer about the request; 5xx is the
            // service failing, which is an outage from the caller's point of view.
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("BREAK-GLASS: grant validation refused with {} — treated as NO_GRANT, "
                        + "because the trust plane answered.", e.getStatusCode());
                return Outcome.NO_GRANT;
            }
            log.error("BREAK-GLASS: grant validation failed with {} — treated as UNREACHABLE.",
                    e.getStatusCode(), e);
            return Outcome.UNREACHABLE;

        } catch (Exception e) {
            // Connect refused, DNS, read timeout, TLS. NOT a refusal — see the class javadoc. If you
            // are tempted to make this NO_GRANT "to be safe", it is the opposite of safe on a path
            // that includes uncrossmatched blood release.
            log.error("BREAK-GLASS: grant validation could not reach tshepo-authz ({}) — treated as "
                    + "UNREACHABLE, so the emergency action proceeds and is recorded for review.",
                    e.getClass().getSimpleName(), e);
            return Outcome.UNREACHABLE;
        }
    }
}
