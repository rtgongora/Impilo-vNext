package zw.gov.mohcc.impilo.experience.routeguard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Does a caller's roles open a given web-shell route?
 *
 * <p>The web shell answers a failed {@code role} guard with {@code router.replace("/home")} — no
 * message, no denial screen. A link a caller cannot open therefore does not read as "not for you",
 * it reads as "this is broken". Any surface that composes a list of destinations has to filter it.
 * The shell does this in {@code src/lib/auth/route-reachability.ts}, reading the requirement off
 * the route registry so a guard change cannot leave a navigation surface pointing at a shut door.</p>
 *
 * <p>The mobile provider hubs are composed here instead, and this service cannot import
 * {@code routes.ts}. Hand-copying the role requirements into Java is what
 * {@code route-reachability.ts} exists to prevent, and is how the shell and this service drifted
 * apart over {@code /coverage}. So the registry is exported instead: {@code routes.ts} remains the
 * only place a guard is declared, and {@code route-guards.generated.json} is a generated
 * projection of it, kept honest by {@code npm run test:route-guard-export} in CI.</p>
 *
 * <p>Scope is the ROLE dimension only, matching the shell helper. Facility, workspace, shift and
 * provider guards redirect into a context-selection flow, which is a legitimate continuation
 * rather than a dead end.</p>
 */
@Component
public class RouteGuardRegistry {

    private static final String RESOURCE = "route-guards.generated.json";

    /** {@code [param]} segments, as the shell's matchRouteDefinition() treats them. */
    private static final Pattern DYNAMIC_SEGMENT = Pattern.compile("\\[(\\w+)]");

    private final List<CompiledRoute> routes;
    private final Map<String, Set<String>> roleGroups;

    public RouteGuardRegistry() {
        this(RESOURCE);
    }

    RouteGuardRegistry(String resourceName) {
        JsonNode root = read(resourceName);

        Map<String, Set<String>> groups = new LinkedHashMap<>();
        JsonNode groupsNode = root.get("roleGroups");
        if (groupsNode == null || !groupsNode.isObject() || groupsNode.isEmpty()) {
            throw new IllegalStateException(resourceName + " has no roleGroups");
        }
        groupsNode.fields().forEachRemaining(entry -> {
            Set<String> expanded = new HashSet<>();
            entry.getValue().forEach(role -> expanded.add(role.asText()));
            groups.put(entry.getKey(), Set.copyOf(expanded));
        });

        List<CompiledRoute> compiled = new ArrayList<>();
        JsonNode routesNode = root.get("routes");
        if (routesNode == null || !routesNode.isArray() || routesNode.isEmpty()) {
            throw new IllegalStateException(resourceName + " has no routes");
        }
        for (JsonNode route : routesNode) {
            String path = route.path("path").asText(null);
            String guard = route.path("guard").asText(null);
            if (path == null || guard == null) {
                throw new IllegalStateException(resourceName + " has a route with no path or guard");
            }
            JsonNode required = route.get("requiredRole");
            compiled.add(new CompiledRoute(
                    compile(path),
                    guard,
                    required == null ? null : required.asText()));
        }

        this.routes = List.copyOf(compiled);
        this.roleGroups = Map.copyOf(groups);
    }

    private static JsonNode read(String resourceName) {
        ClassPathResource resource = new ClassPathResource(resourceName);
        if (!resource.exists()) {
            // Failing to start is correct. Silently admitting everything would restore exactly the
            // defect this class exists to remove, and would do it invisibly.
            throw new IllegalStateException(
                    "Missing " + resourceName + " on the classpath. Generate it with "
                            + "`npm run generate:route-guards` in ui/one-ui-shell.");
        }
        try (InputStream in = resource.getInputStream()) {
            return new ObjectMapper().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + resourceName, e);
        }
    }

    /**
     * Mirrors the shell's matchRouteDefinition(): {@code [param]} becomes one non-slash segment,
     * everything else is literal, anchored at both ends.
     */
    private static Pattern compile(String routePath) {
        StringBuilder regex = new StringBuilder("^");
        Matcher matcher = DYNAMIC_SEGMENT.matcher(routePath);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                regex.append(Pattern.quote(routePath.substring(last, matcher.start())));
            }
            regex.append("[^/]+");
            last = matcher.end();
        }
        if (last < routePath.length()) {
            regex.append(Pattern.quote(routePath.substring(last)));
        }
        return Pattern.compile(regex.append("$").toString());
    }

    /**
     * Would the web shell's role guard let these roles open {@code path}?
     *
     * <p>An unrecognised path is admitted, matching the shell helper: not knowing of a refusal is
     * not the same as knowing of one, and hiding unknown destinations would quietly delete working
     * links whenever this projection lagged the registry. The projection going stale is caught in
     * CI rather than papered over here.</p>
     */
    public boolean admits(String path, Collection<String> roles) {
        if (path == null || path.isBlank()) {
            return true;
        }
        CompiledRoute route = match(path);
        if (route == null || !"role".equals(route.guard()) || route.requiredRole() == null) {
            return true;
        }
        return matchesRequiredRole(roles, route.requiredRole());
    }

    /** First match wins, exactly as the shell iterates ROUTES in declaration order. */
    private CompiledRoute match(String path) {
        for (CompiledRoute route : routes) {
            if (route.pattern().matcher(path).matches()) {
                return route;
            }
        }
        return null;
    }

    /** Mirrors matchesRequiredRole(): a group name expands, anything else is a literal role. */
    private boolean matchesRequiredRole(Collection<String> roles, String requiredRole) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        Set<String> group = roleGroups.get(requiredRole);
        if (group != null) {
            return roles.stream().anyMatch(group::contains);
        }
        return roles.contains(requiredRole);
    }

    /**
     * Is {@code path} declared in the route registry at all?
     *
     * <p>Distinct from {@link #admits}: an unknown path is *admitted*, so a hub pointing at a
     * route that no longer exists is invisible to the filter. This lets a test assert that the
     * destinations a hub ships are real.</p>
     */
    public boolean isRegistered(String path) {
        return path != null && !path.isBlank() && match(path) != null;
    }

    /** Number of routes in the projection — used by tests to prove it loaded something real. */
    public int size() {
        return routes.size();
    }

    private record CompiledRoute(Pattern pattern, String guard, String requiredRole) {
    }
}
