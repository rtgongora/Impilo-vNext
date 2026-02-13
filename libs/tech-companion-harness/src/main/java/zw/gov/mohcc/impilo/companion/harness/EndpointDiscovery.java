package zw.gov.mohcc.impilo.companion.harness;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Discovers v1.1 endpoints registered in the Spring application context.
 *
 * Uses {@link RequestMappingHandlerMapping} to introspect actual controller
 * mappings at test time. This avoids the need for services to expose
 * dedicated mock/probe endpoints for contract testing.
 *
 * Categories:
 * <ul>
 *   <li><b>Read endpoint</b>: any GET mapping under /internal/v1/ or /external/v1/</li>
 *   <li><b>Command endpoint</b>: any POST/PUT/PATCH mapping under /internal/v1/</li>
 *   <li><b>Federation endpoint</b>: detected by annotation or naming convention (not auto-detectable;
 *       falls back to the service providing it via override)</li>
 * </ul>
 */
public final class EndpointDiscovery {

    private static final Set<String> COMMAND_METHODS = Set.of("POST", "PUT", "PATCH");

    private EndpointDiscovery() {
    }

    /**
     * Find a GET endpoint on a v1.1 path.
     */
    public static Optional<String> findReadEndpoint(RequestMappingHandlerMapping mapping) {
        return findEndpointByMethod(mapping, "GET");
    }

    /**
     * Find a POST/PUT/PATCH endpoint on /internal/v1/ for idempotency testing.
     */
    public static Optional<String> findCommandEndpoint(RequestMappingHandlerMapping mapping) {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = mapping.getHandlerMethods();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo info = entry.getKey();

            // Check if any of the request methods are command methods
            Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                    info.getMethodsCondition().getMethods();
            boolean hasCommandMethod = methods.stream()
                    .anyMatch(m -> COMMAND_METHODS.contains(m.name()));

            if (!hasCommandMethod) {
                continue;
            }

            // Check if any pattern is under /internal/v1/
            if (info.getPathPatternsCondition() != null) {
                Optional<String> match = info.getPathPatternsCondition().getPatterns().stream()
                        .map(Object::toString)
                        .filter(p -> p.startsWith("/internal/v1/"))
                        .findFirst();
                if (match.isPresent()) {
                    return match;
                }
            }

            // Fallback: direct patterns
            if (info.getDirectPaths() != null) {
                Optional<String> match = info.getDirectPaths().stream()
                        .filter(p -> p.startsWith("/internal/v1/"))
                        .findFirst();
                if (match.isPresent()) {
                    return match;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Find an endpoint by HTTP method on v1.1 paths.
     */
    private static Optional<String> findEndpointByMethod(
            RequestMappingHandlerMapping mapping, String httpMethod) {

        Map<RequestMappingInfo, HandlerMethod> handlerMethods = mapping.getHandlerMethods();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo info = entry.getKey();

            Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                    info.getMethodsCondition().getMethods();
            boolean matchesMethod = methods.isEmpty() ||
                    methods.stream().anyMatch(m -> m.name().equals(httpMethod));

            if (!matchesMethod) {
                continue;
            }

            // Check path patterns
            if (info.getPathPatternsCondition() != null) {
                Optional<String> match = info.getPathPatternsCondition().getPatterns().stream()
                        .map(Object::toString)
                        .filter(EndpointDiscovery::isV11Path)
                        .findFirst();
                if (match.isPresent()) {
                    return match;
                }
            }

            // Fallback: direct paths
            if (info.getDirectPaths() != null) {
                Optional<String> match = info.getDirectPaths().stream()
                        .filter(EndpointDiscovery::isV11Path)
                        .findFirst();
                if (match.isPresent()) {
                    return match;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if a path is a v1.1 endpoint path.
     */
    public static boolean isV11Path(String path) {
        return path != null &&
                (path.startsWith("/internal/v1/") || path.startsWith("/external/v1/"));
    }

    /**
     * Get the HTTP method for a command endpoint (prefers POST).
     */
    public static String getCommandMethod(RequestMappingHandlerMapping mapping, String path) {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = mapping.getHandlerMethods();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo info = entry.getKey();

            boolean pathMatches = false;
            if (info.getPathPatternsCondition() != null) {
                pathMatches = info.getPathPatternsCondition().getPatterns().stream()
                        .anyMatch(p -> p.toString().equals(path));
            }
            if (!pathMatches && info.getDirectPaths() != null) {
                pathMatches = info.getDirectPaths().contains(path);
            }

            if (pathMatches) {
                return info.getMethodsCondition().getMethods().stream()
                        .filter(m -> COMMAND_METHODS.contains(m.name()))
                        .map(Enum::name)
                        .findFirst()
                        .orElse("POST");
            }
        }
        return "POST";
    }
}
