package zw.gov.mohcc.impilo.msikaflow.api;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;
import java.util.UUID;

/**
 * Extracts trust headers from HTTP requests.
 * In production, use shared-core TrustContextFilter; this is a local fallback.
 */
public final class TrustHeaderExtractor {

    public static final String H_TENANT_ID = "X-Tenant-Id";
    public static final String H_CORRELATION_ID = "X-Correlation-Id";
    public static final String H_DEVICE_FINGERPRINT = "X-Device-Fingerprint";
    public static final String H_PURPOSE_OF_USE = "X-Purpose-Of-Use";
    public static final String H_ACTOR_ID = "X-Actor-Id";
    public static final String H_ACTOR_TYPE = "X-Actor-Type";
    public static final String H_FACILITY_ID = "X-Facility-Id";
    public static final String H_WORKSPACE_ID = "X-Workspace-Id";
    public static final String H_SHIFT_ID = "X-Shift-Id";
    public static final String H_ASSURANCE_LEVEL = "X-Assurance-Level";

    private TrustHeaderExtractor() {}

    public static UUID tenantId(HttpServletRequest req) {
        String val = req.getHeader(H_TENANT_ID);
        if (val == null || val.isBlank()) throw new IllegalArgumentException("Missing X-Tenant-Id header");
        return UUID.fromString(val.trim());
    }

    public static String actorId(HttpServletRequest req) {
        String val = req.getHeader(H_ACTOR_ID);
        if (val == null || val.isBlank()) throw new IllegalArgumentException("Missing X-Actor-Id header");
        return val.trim();
    }

    public static String actorType(HttpServletRequest req) {
        String val = req.getHeader(H_ACTOR_TYPE);
        return val != null ? val.trim() : "SYSTEM";
    }

    public static String correlationId(HttpServletRequest req) {
        String val = req.getHeader(H_CORRELATION_ID);
        return val != null ? val.trim() : UUID.randomUUID().toString();
    }

    public static UUID facilityId(HttpServletRequest req) {
        String val = req.getHeader(H_FACILITY_ID);
        if (val == null || val.isBlank()) return null;
        return UUID.fromString(val.trim());
    }

    public static String deviceFingerprint(HttpServletRequest req) {
        String val = req.getHeader(H_DEVICE_FINGERPRINT);
        return val != null ? val.trim() : "unknown";
    }

    public static String purposeOfUse(HttpServletRequest req) {
        String val = req.getHeader(H_PURPOSE_OF_USE);
        return val != null ? val.trim() : "OPERATIONS";
    }

    /** Parses {@value H_ASSURANCE_LEVEL} (e.g. LOA3 → 3). */
    public static Integer assuranceLevel(HttpServletRequest req) {
        String raw = req.getHeader(H_ASSURANCE_LEVEL);
        if (raw == null || raw.isBlank()) {
            raw = req.getHeader(H_ASSURANCE_LEVEL.toLowerCase(Locale.ROOT));
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        raw = raw.trim();
        if (raw.toUpperCase(Locale.ROOT).startsWith("LOA")) {
            try {
                return Integer.parseInt(raw.substring(3).trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
