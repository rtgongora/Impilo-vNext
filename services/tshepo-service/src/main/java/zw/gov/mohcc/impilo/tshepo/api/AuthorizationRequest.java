package zw.gov.mohcc.impilo.tshepo.api;

import zw.gov.mohcc.impilo.tshepo.core.TrustHeaders;

import java.util.UUID;

/**
 * Represents the parsed authorization check request from Envoy ext_authz.
 * Constructed from HTTP headers forwarded by Envoy.
 */
public record AuthorizationRequest(
    UUID tenantId,
    String actorId,
    String actorType,
    String purposeOfUse,
    String deviceFingerprint,
    UUID correlationId,
    UUID facilityId,
    UUID workspaceId,
    String shiftId,
    String action,
    String resourceType,
    String resourceId
) {
    /**
     * Validates that all mandatory fields are present.
     */
    public boolean isValid() {
        return tenantId != null
            && actorId != null && !actorId.isBlank()
            && actorType != null && !actorType.isBlank()
            && purposeOfUse != null && !purposeOfUse.isBlank()
            && correlationId != null;
    }

    /**
     * Parse from HTTP headers + request method/path.
     */
    public static AuthorizationRequest fromHeaders(
            java.util.function.Function<String, String> headerGetter,
            String method,
            String path) {

        return new AuthorizationRequest(
            parseUuid(headerGetter.apply(TrustHeaders.TENANT_ID)),
            headerGetter.apply(TrustHeaders.ACTOR_ID),
            headerGetter.apply(TrustHeaders.ACTOR_TYPE),
            headerGetter.apply(TrustHeaders.PURPOSE_OF_USE),
            headerGetter.apply(TrustHeaders.DEVICE_FINGERPRINT),
            parseUuidOrGenerate(headerGetter.apply(TrustHeaders.CORRELATION_ID)),
            parseUuid(headerGetter.apply(TrustHeaders.FACILITY_ID)),
            parseUuid(headerGetter.apply(TrustHeaders.WORKSPACE_ID)),
            headerGetter.apply(TrustHeaders.SHIFT_ID),
            deriveAction(method, path),
            deriveResourceType(path),
            deriveResourceId(path)
        );
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID parseUuidOrGenerate(String value) {
        UUID parsed = parseUuid(value);
        return parsed != null ? parsed : UUID.randomUUID();
    }

    private static String deriveAction(String method, String path) {
        if (method == null) return "UNKNOWN";
        return method.toUpperCase() + ":" + (path != null ? path : "/");
    }

    private static String deriveResourceType(String path) {
        if (path == null || path.length() < 2) return "UNKNOWN";
        // Extract resource type from path: /api/v1/clients -> clients
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i];
            if (!seg.isBlank() && !seg.matches("[0-9a-fA-F-]{36}") && !seg.equals("v1") && !seg.equals("api")) {
                return seg;
            }
        }
        return "UNKNOWN";
    }

    private static String deriveResourceId(String path) {
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
