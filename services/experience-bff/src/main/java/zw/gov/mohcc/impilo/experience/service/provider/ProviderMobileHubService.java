package zw.gov.mohcc.impilo.experience.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.OneUiShellMobileHubClient;
import zw.gov.mohcc.impilo.experience.config.BffProviderHubsProperties;
import zw.gov.mohcc.impilo.experience.routeguard.RouteGuardRegistry;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProviderMobileHubService {

    private static final Logger log = LoggerFactory.getLogger(ProviderMobileHubService.class);

    private final BffProviderHubsProperties properties;
    private final OneUiShellMobileHubClient oneUiShellMobileHubClient;
    private final RouteGuardRegistry routeGuards;

    public ProviderMobileHubService(
            BffProviderHubsProperties properties,
            OneUiShellMobileHubClient oneUiShellMobileHubClient,
            RouteGuardRegistry routeGuards) {
        this.properties = properties;
        this.oneUiShellMobileHubClient = oneUiShellMobileHubClient;
        this.routeGuards = routeGuards;
    }

    /**
     * Sections of {@code hub} that the calling roles can actually open.
     *
     * <p>Filtering happens here rather than in each controller so it covers every path that can
     * produce a catalogue — the live one-ui-shell response and the local stub alike. A section
     * whose route the caller's roles refuse is not a link; the shell answers a failed role guard
     * with {@code router.replace("/home")}, and on mobile the tap has already thrown the person
     * out of the app into a browser by then.</p>
     */
    public List<Map<String, Object>> sectionsForHub(String hub, List<Map<String, Object>> stub) {
        return reachable(hub, resolveSections(hub, stub));
    }

    private List<Map<String, Object>> resolveSections(String hub, List<Map<String, Object>> stub) {
        if (properties.getMode() == BffProviderHubsProperties.Mode.stub) {
            return stub;
        }
        try {
            JsonNode root = oneUiShellMobileHubClient.getProviderHub(hub);
            JsonNode sections = root != null ? root.get("sections") : null;
            if (sections != null && sections.isArray()) {
                // Preserve the JSON shape from one-ui-shell without inventing new fields.
                // Convert to Map list via Jackson's natural tree-to-Map conversion.
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) new com.fasterxml.jackson.databind.ObjectMapper()
                        .convertValue(sections, List.class);
                return list;
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "one-ui-shell hub missing sections: " + hub);
        } catch (RestClientException e) {
            log.warn("Provider hub {} downstream failure: {}", hub, e.getMessage());
            if (properties.getFailurePolicy() == BffProviderHubsProperties.FailurePolicy.propagate) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "downstream unavailable: provider hub " + hub, e);
            }
            return stub;
        }
    }

    private List<Map<String, Object>> reachable(String hub, List<Map<String, Object>> sections) {
        if (sections == null || sections.isEmpty()) {
            return sections;
        }
        Set<String> roles = callerRoles();
        List<Map<String, Object>> open = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        for (Map<String, Object> section : sections) {
            Object webPath = section.get("web_path");
            String path = webPath == null ? null : webPath.toString();
            if (routeGuards.admits(path, roles)) {
                open.add(section);
            } else {
                refused.add(path);
            }
        }
        if (!refused.isEmpty()) {
            // Say what was dropped. A silently shortened catalogue reads as "this hub is small",
            // and that is indistinguishable from the projection having gone wrong.
            log.debug("Provider hub {}: withheld {} of {} sections the caller's roles refuse: {}",
                    hub, refused.size(), sections.size(), refused);
        }
        return open;
    }

    private static Set<String> callerRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                // Keycloak realm roles arrive as ROLE_<name> (KeycloakRealmRoleConverter); the
                // route registry declares the bare realm role.
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .collect(Collectors.toSet());
    }

    public Map<String, Object> hubEnvelope(String requestId, String correlationId, List<Map<String, Object>> sections) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", Map.of(
                "refreshed_at", OffsetDateTime.now(),
                "sections", sections
        ));
        body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return body;
    }
}

