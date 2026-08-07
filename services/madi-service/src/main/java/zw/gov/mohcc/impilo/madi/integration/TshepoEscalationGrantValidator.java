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
 *   200 + {"outcome":"VALID"}     → VALID         the trust plane answered: a grant holds
 *   200 + {"outcome":"NO_GRANT"}  → NO_GRANT      the trust plane answered: no grant holds
 *   anything else                 → UNREACHABLE   the question was not answered
 * </pre>
 *
 * <p><b>Only an explicit 200 with a recognised outcome is an answer.</b> This endpoint returns 200
 * for every real verdict, so a 401, 403, 404, 5xx, timeout or unparseable body all mean the same
 * thing: we did not get an answer. An earlier version of this class mapped 4xx to {@code NO_GRANT}
 * on the reasoning that "the service answered, so it is not an outage". That was wrong in a way that
 * only shows up in production: a missing token, a stale route or a misconfigured base URL would have
 * turned into "no grant" on every call and refused every emergency action — silently, and with a
 * clinical consequence. A misconfiguration is not a statement about whether a clinician holds a
 * grant.</p>
 *
 * <p>The cost of the safer mapping is that a caller who can reach this service at all could send a
 * deliberately broken request to force the override branch. That is accepted rather than overlooked:
 * the override is <em>recorded, attributed and flagged for review</em>, so it is visible, whereas a
 * blocked transfusion is silent. Visible-and-wrong beats silent-and-wrong on this path.</p>
 *
 * <p><b>There is deliberately no catch-all returning NO_GRANT.</b> The single most likely way to
 * rebuild the defect this replaced is a {@code catch (Exception e) { return NO_GRANT; }} — it looks
 * conservative, reads as fail-closed, and would block an emergency transfusion every time
 * tshepo-authz restarts. The final catch here returns {@link Outcome#UNREACHABLE} and says why.</p>
 *
 * <p>The inverse mistake — treating an outage as VALID — is worse and is not reachable: VALID is
 * returned only on an explicit {@code "VALID"} string in a parsed 200 body.</p>
 */
@Service
public class TshepoEscalationGrantValidator implements EscalationGrantValidator {

    private static final Logger log = LoggerFactory.getLogger(TshepoEscalationGrantValidator.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TshepoEscalationGrantValidator(
            @org.springframework.beans.factory.annotation.Qualifier("trustCallRestTemplate")
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
        // x-actor-id is deliberately NOT sent as an authority claim. tshepo-authz binds the actor to
        // the bearer token's subject; a header here would be ignored, and sending one would invite
        // the belief that a caller may ask about somebody else.

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
            // Any non-200 means the question was not answered. 401/403 in particular is the shape a
            // missing or expired workload credential takes, and treating that as "no grant" would
            // refuse every emergency action in the estate the moment auth drifted.
            log.error("BREAK-GLASS: grant validation returned {} — the question was NOT answered, so "
                            + "this is UNREACHABLE, not a refusal.", e.getStatusCode(), e);
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
