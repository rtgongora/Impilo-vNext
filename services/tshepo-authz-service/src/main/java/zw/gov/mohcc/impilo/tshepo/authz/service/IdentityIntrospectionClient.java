package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;

import java.util.Map;

/**
 * Resolves a WORK_CONTEXT duty token into a {@link DutyContext} by calling
 * tshepo-identity {@code POST /v1/identity/tokens/introspect}.
 *
 * <p>Server-side validity (active / not-revoked / not-expired) is enforced by the
 * identity service; the operational context lives in the token's {@code contextClaims}.
 * The PDP does not re-verify the JWS or hold the signing key — introspection is the
 * single trust anchor, so there is no new crypto in the authz plane.</p>
 *
 * <p><b>Fail-open, by design.</b> This is a SHADOW-first ADD to authorization, never a
 * replacement for authn: if identity is unavailable we return {@link DutyContext#absent()}
 * and log, so a transient identity outage can never harden into a mass denial. The
 * ENFORCE decision (deny on mismatch) lives in the PolicyEngine and applies only to a
 * token that WAS successfully introspected and found mismatched/revoked.</p>
 */
@Service
public class IdentityIntrospectionClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityIntrospectionClient.class);

    private final RestTemplate restTemplate;
    private final AuthzProperties properties;

    public IdentityIntrospectionClient(RestTemplate restTemplate, AuthzProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * Introspect a WORK_CONTEXT token.
     *
     * @param token the raw signed token from {@code X-Work-Context-Token}
     * @return the resolved duty context; {@link DutyContext#absent()} on any error
     *         (fail-open), {@link DutyContext#revoked()} when identity says inactive.
     */
    public DutyContext introspect(String token) {
        if (token == null || token.isBlank()) {
            return DutyContext.absent();
        }
        try {
            String url = properties.getIdentityServiceUrl() + properties.getWorkContextIntrospectPath();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("token", token), headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);
            JsonNode body = response.getBody();
            if (body == null) {
                log.warn("Work-context introspection returned empty body");
                return DutyContext.absent();
            }
            // ApiResponse envelope: { data: { active, tokenKind, actorId, contextClaims{...} }, ... }
            JsonNode data = body.has("data") ? body.get("data") : body;
            boolean active = data.path("active").asBoolean(false);
            if (!active) {
                return DutyContext.revoked();
            }
            JsonNode claims = data.path("contextClaims");
            return new DutyContext(
                    true,
                    true,
                    text(data, "tokenKind"),
                    text(data, "actorId"),
                    text(claims, "facility_id"),
                    text(claims, "workspace_id"),
                    text(claims, "department_id"),
                    text(claims, "ward_id"),
                    text(claims, "programme_id"),
                    text(claims, "org_id"),
                    text(claims, "provider_id"),
                    text(claims, "role"),
                    text(claims, "assignment_id"));
        } catch (Exception e) {
            // Fail-open: identity outage must never become an authorization outage.
            log.warn("Work-context introspection failed (fail-open, treated as absent): {}", e.getMessage());
            return DutyContext.absent();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node != null ? node.get(field) : null;
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
