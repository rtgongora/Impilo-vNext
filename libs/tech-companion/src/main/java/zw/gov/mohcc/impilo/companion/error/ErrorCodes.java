package zw.gov.mohcc.impilo.companion.error;

/**
 * Standard v1.1 error codes used across all Impilo services.
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    // ── Header Enforcement ──────────────────────────────────────
    public static final String MISSING_REQUIRED_HEADER = "MISSING_REQUIRED_HEADER";

    // ── Idempotency ─────────────────────────────────────────────
    public static final String IDEMPOTENCY_KEY_REQUIRED = "IDEMPOTENCY_KEY_REQUIRED";
    public static final String IDENTITY_CONFLICT = "IDENTITY_CONFLICT";

    // ── Timeout ─────────────────────────────────────────────────
    public static final String CLIENT_TIMEOUT_EXCEEDED = "CLIENT_TIMEOUT_EXCEEDED";

    // ── Federation ──────────────────────────────────────────────
    public static final String FEDERATION_AUTHORITY_VIOLATION = "FEDERATION_AUTHORITY_VIOLATION";

    // ── Authentication / Authorization ──────────────────────────
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";

    // ── Validation ──────────────────────────────────────────────
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    // ── Consistency Class Enforcement ────────────────────────────
    public static final String PDP_UNAVAILABLE = "PDP_UNAVAILABLE";
    public static final String STALE_CONTEXT = "STALE_CONTEXT";
    public static final String OFFLINE_ENTITLEMENT_REQUIRED = "OFFLINE_ENTITLEMENT_REQUIRED";
    public static final String BREAK_GLASS_NOT_ALLOWED = "BREAK_GLASS_NOT_ALLOWED";

    // ── General ─────────────────────────────────────────────────
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
}
