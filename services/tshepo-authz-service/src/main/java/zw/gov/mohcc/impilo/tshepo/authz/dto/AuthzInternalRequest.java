package zw.gov.mohcc.impilo.tshepo.authz.dto;

import java.util.List;
import java.util.UUID;

/**
 * Internal authorization request used by the PolicyEngine.
 * Aggregates all context needed to make an authorization decision.
 */
public record AuthzInternalRequest(
        UUID tenantId,
        String actorId,
        String actorType,
        List<String> roles,
        String purposeOfUse,
        String deviceFingerprint,
        UUID correlationId,
        UUID facilityId,
        UUID workspaceId,
        String shiftId,
        String method,
        String path,
        String action,
        String resourceType,
        String resourceId,
        int loaLevel,
        String sessionId,
        String bearerToken
) {
    /**
     * Derive a human-readable action from method + path.
     */
    public static String deriveAction(String method, String path) {
        if (method == null) return "UNKNOWN";
        return method.toUpperCase() + ":" + (path != null ? path : "/");
    }

    /**
     * Derive resource type from the URL path.
     */
    public static String deriveResourceType(String path) {
        if (path == null || path.length() < 2) return "UNKNOWN";
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i];
            if (!seg.isBlank() && !seg.matches("[0-9a-fA-F-]{36}") && !seg.equals("v1") && !seg.equals("api")) {
                return seg;
            }
        }
        return "UNKNOWN";
    }

    /**
     * Derive resource ID (UUID) from the URL path.
     */
    public static String deriveResourceId(String path) {
        if (path == null) return null;
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            if (segments[i].matches("[0-9a-fA-F-]{36}")) {
                return segments[i];
            }
        }
        return null;
    }
}
