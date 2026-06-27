package zw.gov.mohcc.impilo.experience.config;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;

import java.util.Locale;

/**
 * Resolves a CITIZEN caller's <em>current</em> identity-assurance level once per inbound
 * request and stashes it as a request attribute, so the {@code ServiceClientConfig} trust-header
 * interceptor can set {@code X-Assurance-Level} <strong>authoritatively</strong> on downstream
 * calls (overriding any client-supplied value).
 *
 * <p>This closes the LOA-propagation break (G-CZO-01): identity-assurance-service is the canonical
 * owner of the assurance level and persists self-service upgrades, but nothing previously fed that
 * level into the trust plane — the policy engine only ever saw the frozen Keycloak ACR level. With
 * this resolver the upgrade actually changes what policy sees on the very next request.</p>
 *
 * <p>Why a Spring MVC {@link HandlerInterceptor} (not a servlet filter): {@code preHandle} runs
 * inside DispatcherServlet processing, so {@code RequestContextHolder} is bound and the IA client's
 * outbound call inherits the inbound trust headers (tenant/actor). The IA call is an outbound
 * RestTemplate call and does not re-enter MVC interceptors, so there is no recursion.</p>
 *
 * <p>Fail-safe: any error (IA unreachable, missing level) leaves the attribute unset, and the trust
 * interceptor falls back to forwarding the inbound header — i.e. the prior ACR-only behaviour. The
 * resolver never grants; it can only surface a real, persisted level. The header is set
 * authoritatively (overwritten) so a client cannot forge a higher level.</p>
 */
@Component
public class AssuranceLevelResolutionInterceptor implements HandlerInterceptor {

    /** Request attribute holding the resolved canonical assurance level (e.g. "LOA3"). */
    public static final String RESOLVED_ASSURANCE_LEVEL_ATTR =
            "impilo.experience.resolvedAssuranceLevel";

    private static final Logger log = LoggerFactory.getLogger(AssuranceLevelResolutionInterceptor.class);
    private static final String CITIZEN = "CITIZEN";

    // ObjectProvider so the interceptor wires in any context (incl. @WebMvcTest slices that do
    // not load the client bean); resolution simply no-ops when the client is unavailable.
    private final ObjectProvider<IdentityAssuranceServiceClient> identityAssuranceClient;

    public AssuranceLevelResolutionInterceptor(
            ObjectProvider<IdentityAssuranceServiceClient> identityAssuranceClient) {
        this.identityAssuranceClient = identityAssuranceClient;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String actorId = request.getHeader(CompanionHeaders.ACTOR_ID);
        String actorType = request.getHeader(CompanionHeaders.ACTOR_TYPE);
        // Only resolve for an authenticated citizen actor. Providers' min_loa is keyed on their
        // ACR login level; resolving citizen assurance for them would be both wrong and wasteful.
        if (actorId == null || actorId.isBlank()
                || actorType == null || !CITIZEN.equalsIgnoreCase(actorType.trim())) {
            return true;
        }
        IdentityAssuranceServiceClient client = identityAssuranceClient.getIfAvailable();
        if (client == null) {
            return true; // no client bean in this context (e.g. sliced web test) — skip
        }
        try {
            JsonNode status = client.getAssuranceStatus();
            String level = status == null ? null : status.path("currentLevel").asText(null);
            if (level != null && !level.isBlank()) {
                request.setAttribute(RESOLVED_ASSURANCE_LEVEL_ATTR,
                        level.trim().toUpperCase(Locale.ROOT));
            }
        } catch (Exception e) {
            // Fail-safe: leave the attribute unset; policy falls back to the ACR level.
            log.debug("Assurance-level resolution skipped for actor={}: {}", actorId, e.getMessage());
        }
        return true;
    }
}
