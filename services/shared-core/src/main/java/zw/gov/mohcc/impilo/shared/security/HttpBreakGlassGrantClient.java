package zw.gov.mohcc.impilo.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.WorkloadTokenProvider;
import zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantCheck;
import zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantClient;
import zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantQuery;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Calls {@code POST /v1/break-glass/validate} on tshepo-authz.
 *
 * <h2>The mapping is the whole point of this class</h2>
 *
 * <p>Exactly one thing produces {@link BreakGlassGrantCheck#noGrant()}: a 2xx response whose body
 * says {@code "verdict": "NO_GRANT"}. Everything else — a timeout, a connection refusal, a 401 from
 * an expired service credential, a 404 from a wrong base URL, a 503, an empty body, a verdict
 * string nobody recognises — is {@link BreakGlassGrantCheck#unreachable(String)}.</p>
 *
 * <p>Both directions of that mapping are safety-critical, and they fail differently:</p>
 * <ul>
 *   <li>Mapping an outage to NO_GRANT would refuse an emergency transfusion because a policy
 *       service was down, or because someone typo'd a URL in a values file.</li>
 *   <li>Mapping a NO_GRANT to UNREACHABLE would silently reopen the forged-purpose hole, with the
 *       audit record misdescribing it as an outage.</li>
 * </ul>
 *
 * <p>So there is no shared default and no fall-through: the verdict string is matched explicitly
 * and anything unmatched is UNREACHABLE. This method never throws — see
 * {@link BreakGlassGrantClient} for why that is a contract rather than a convenience.</p>
 *
 * <h2>Which credential is presented</h2>
 *
 * <p>Preferred: this workload's own client-credentials token, via {@link WorkloadTokenProvider}.
 * That is the correct shape for an east-west call and it works on background threads with no
 * inbound request.</p>
 *
 * <p>Fallback: the bearer token of the request currently being served. This is deliberate, not a
 * shortcut. Most services in the estate do not yet have a per-workload Keycloak client — at the
 * time of writing, {@code impilo.s2s.token.enabled} defaults to false and neither madi nor pct sets
 * it — and without a fallback every emergency action would resolve UNREACHABLE, leaving the
 * refusing branch dead in the deployed estate and the forged-purpose hole open in practice while
 * looking closed in code. Forwarding the caller's own token is also the honest authority here: the
 * question being asked is "does <em>this actor</em> hold a grant", and the actor's token is
 * evidence they are that actor. It grants nothing the caller did not already have, because the
 * endpoint is read-only and returns no entitlements.</p>
 *
 * <p>Neither available — a background thread with no workload credential — is UNREACHABLE, with a
 * detail that says so, so the resulting override record names a misconfiguration rather than an
 * outage.</p>
 */
public class HttpBreakGlassGrantClient implements BreakGlassGrantClient {

    private static final Logger log = LoggerFactory.getLogger(HttpBreakGlassGrantClient.class);

    static final String VALIDATE_PATH = "/v1/break-glass/validate";

    private static final String VERDICT_VALID = "VALID";
    private static final String VERDICT_NO_GRANT = "NO_GRANT";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final WorkloadTokenProvider tokenProvider;

    /**
     * @param restTemplate  should carry short connect/read timeouts — this sits on an emergency
     *                      clinical path, where a slow answer is worse than a prompt UNREACHABLE
     * @param baseUrl       tshepo-authz base URL; blank means "not configured", which is UNREACHABLE
     * @param tokenProvider this workload's credential source; may be null where none is wired
     */
    public HttpBreakGlassGrantClient(RestTemplate restTemplate, String baseUrl,
                                     WorkloadTokenProvider tokenProvider) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public BreakGlassGrantCheck check(BreakGlassGrantQuery query) {
        if (restTemplate == null || baseUrl == null || baseUrl.isBlank()) {
            return BreakGlassGrantCheck.unreachable(
                    "tshepo-authz base URL is not configured for this service — the grant could not be checked");
        }

        Optional<String> bearer = resolveBearer();
        if (bearer.isEmpty()) {
            return BreakGlassGrantCheck.unreachable(
                    "no credential available to call tshepo-authz (no workload token configured and no "
                            + "authenticated inbound request) — the grant could not be checked");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            headers.setBearerAuth(bearer.get());

            Map<String, Object> body = new HashMap<>();
            body.put("tenantId", query.tenantId());
            body.put("actorId", query.actorId());
            body.put("grantToken", query.grantToken());
            body.put("action", query.action());

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + VALIDATE_PATH, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                // RestTemplate normally throws on non-2xx; this covers an error handler that does not.
                return BreakGlassGrantCheck.unreachable(
                        "tshepo-authz returned HTTP " + response.getStatusCode().value()
                                + " — this is not an answer about the grant");
            }

            Map<?, ?> payload = response.getBody();
            if (payload == null) {
                return BreakGlassGrantCheck.unreachable("tshepo-authz returned an empty body");
            }

            Object verdict = payload.get("verdict");
            String verdictText = verdict == null ? null : verdict.toString();

            if (VERDICT_VALID.equals(verdictText)) {
                return BreakGlassGrantCheck.valid(asText(payload.get("source")), asText(payload.get("grantRef")));
            }
            if (VERDICT_NO_GRANT.equals(verdictText)) {
                return BreakGlassGrantCheck.noGrant();
            }

            // Deliberately not a default-to-NO_GRANT. An unrecognised verdict means this client and
            // the service disagree about the contract, which is a failure to obtain an answer.
            return BreakGlassGrantCheck.unreachable(
                    "tshepo-authz returned an unrecognised verdict '" + verdictText + "'");

        } catch (Exception e) {
            // Every transport and protocol failure lands here: timeouts, connection refusals, 4xx
            // and 5xx (RestTemplate raises these), deserialisation errors. None of them is a
            // statement about whether a grant exists.
            log.warn("Break-glass grant check could not reach tshepo-authz for actor={} action={}: {}: {}",
                    query.actorId(), query.action(), e.getClass().getSimpleName(), e.getMessage());
            return BreakGlassGrantCheck.unreachable(
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString();
    }

    /** This workload's own token if it has one, otherwise the token of the request being served. */
    private Optional<String> resolveBearer() {
        if (tokenProvider != null) {
            Optional<String> workload = tokenProvider.getAccessToken();
            if (workload.isPresent()) {
                return workload;
            }
        }
        return inboundBearer();
    }

    private Optional<String> inboundBearer() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwt) {
                return Optional.ofNullable(jwt.getToken().getTokenValue());
            }
        } catch (RuntimeException e) {
            // No security context (background thread, test harness). Not an error worth raising —
            // it resolves to UNREACHABLE with an explanatory detail, which is the correct outcome.
            log.debug("No inbound bearer available for the break-glass grant check: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
