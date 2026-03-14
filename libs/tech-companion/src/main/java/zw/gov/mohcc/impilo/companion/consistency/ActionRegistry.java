package zw.gov.mohcc.impilo.companion.consistency;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registry that maps request routes to consistency class enforcement rules.
 *
 * <p>Each service populates this registry with its action entries.
 * The {@link ConsistencyClassFilter} looks up the matching entry for each request
 * to determine the required consistency class.</p>
 *
 * <p>Path matching uses prefix matching with support for path variables:
 * {@code /internal/v1/patients/merge} matches exactly, while
 * {@code /internal/v1/patients/**} matches any sub-path.</p>
 *
 * <p>If no entry matches, the request passes through without consistency enforcement
 * (default: no gate).</p>
 */
public class ActionRegistry {

    private final List<ActionRegistryEntry> entries;

    public ActionRegistry() {
        this.entries = new ArrayList<>();
    }

    public ActionRegistry(List<ActionRegistryEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    public void register(ActionRegistryEntry entry) {
        entries.add(entry);
    }

    /**
     * Find the first matching entry for the given path and HTTP method.
     *
     * @param path       the request URI
     * @param httpMethod the HTTP method (GET, POST, etc.)
     * @return the matching entry, or empty if no match
     */
    public Optional<ActionRegistryEntry> lookup(String path, String httpMethod) {
        for (ActionRegistryEntry entry : entries) {
            if (matchesMethod(entry.httpMethod(), httpMethod) && matchesPath(entry.pathPattern(), path)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public List<ActionRegistryEntry> entries() {
        return List.copyOf(entries);
    }

    private boolean matchesMethod(String pattern, String method) {
        return "*".equals(pattern) || pattern.equalsIgnoreCase(method);
    }

    /**
     * Simple path matching supporting:
     * - Exact match: /internal/v1/patients/merge
     * - Wildcard suffix: /internal/v1/patients/** (matches any sub-path)
     * - Single segment wildcard: /internal/v1/patients/{id} (matches any single segment)
     */
    static boolean matchesPath(String pattern, String path) {
        if (pattern.equals(path)) return true;

        // Wildcard suffix match
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }

        // Single segment wildcard match (e.g., /v1/patients/{id}/merge)
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");
        if (patternParts.length != pathParts.length) return false;

        for (int i = 0; i < patternParts.length; i++) {
            String pp = patternParts[i];
            if (pp.startsWith("{") && pp.endsWith("}")) continue; // wildcard segment
            if (!pp.equals(pathParts[i])) return false;
        }
        return true;
    }
}
