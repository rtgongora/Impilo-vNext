package zw.gov.mohcc.impilo.security.input;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Strict input validation and sanitization utilities for Impilo v1.1 services.
 * <p>
 * All methods are stateless and thread-safe. Validation failures throw
 * {@link InputValidationException} with a safe error code (no PII in messages).
 * <p>
 * Security posture: reject-by-default. Only explicitly allowed characters pass.
 */
public final class InputSanitizer {

    private InputSanitizer() { }

    // --- Character class allowlists ---

    /** UUID v4 pattern (lowercase hex + hyphens) */
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                    Pattern.CASE_INSENSITIVE);

    /** Alphanumeric + limited safe punctuation for general text fields */
    private static final Pattern SAFE_TEXT_PATTERN =
            Pattern.compile("^[\\p{L}\\p{N}\\s.,;:!?'\"()\\-/#+@&_]{0,4096}$");

    /** Alphanumeric + underscore + hyphen + dot (identifiers, codes) */
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_.\\-]{1,255}$");

    /** Dot-notation event type (e.g. impilo.vito.patient.created.v1) */
    private static final Pattern EVENT_TYPE_PATTERN =
            Pattern.compile("^[a-z][a-z0-9_.]{2,127}$");

    /** Numeric ID (positive integer) */
    private static final Pattern NUMERIC_ID_PATTERN =
            Pattern.compile("^[1-9][0-9]{0,18}$");

    /** Email — simplified RFC 5321 (no comments, no IP literals) */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,63}$");

    /** Phone — E.164 format */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^\\+[1-9][0-9]{6,14}$");

    // --- Known dangerous patterns to reject ---

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("<script", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\$\\{", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\{\\{", Pattern.CASE_INSENSITIVE),
            Pattern.compile("UNION\\s+SELECT", Pattern.CASE_INSENSITIVE),
            Pattern.compile("--\\s*$"),
            Pattern.compile(";\\s*(DROP|DELETE|UPDATE|INSERT|ALTER|EXEC)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\x00") // null byte injection
    );

    // ================================================================
    // Validation methods — throw InputValidationException on failure
    // ================================================================

    /**
     * Validate a required string is non-null, non-blank, and within max length.
     */
    public static String requireNonBlank(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InputValidationException("REQUIRED_FIELD_MISSING", fieldName);
        }
        if (value.length() > maxLength) {
            throw new InputValidationException("FIELD_TOO_LONG", fieldName);
        }
        rejectInjection(value, fieldName);
        return value.strip();
    }

    /**
     * Validate and return a UUID string.
     */
    public static String requireUuid(String value, String fieldName) {
        requireNonBlank(value, fieldName, 36);
        if (!UUID_PATTERN.matcher(value.strip()).matches()) {
            throw new InputValidationException("INVALID_UUID", fieldName);
        }
        return value.strip();
    }

    /**
     * Validate a safe text field (general purpose names, descriptions, etc.).
     */
    public static String requireSafeText(String value, String fieldName, int maxLength) {
        requireNonBlank(value, fieldName, maxLength);
        String stripped = value.strip();
        if (!SAFE_TEXT_PATTERN.matcher(stripped).matches()) {
            throw new InputValidationException("UNSAFE_CHARACTERS", fieldName);
        }
        return stripped;
    }

    /**
     * Validate an identifier (codes, slugs, enum values).
     */
    public static String requireIdentifier(String value, String fieldName) {
        requireNonBlank(value, fieldName, 255);
        String stripped = value.strip();
        if (!IDENTIFIER_PATTERN.matcher(stripped).matches()) {
            throw new InputValidationException("INVALID_IDENTIFIER", fieldName);
        }
        return stripped;
    }

    /**
     * Validate an event type string (dot-notation).
     */
    public static String requireEventType(String value, String fieldName) {
        requireNonBlank(value, fieldName, 128);
        String stripped = value.strip();
        if (!EVENT_TYPE_PATTERN.matcher(stripped).matches()) {
            throw new InputValidationException("INVALID_EVENT_TYPE", fieldName);
        }
        return stripped;
    }

    /**
     * Validate a positive numeric ID.
     */
    public static long requireNumericId(String value, String fieldName) {
        requireNonBlank(value, fieldName, 19);
        String stripped = value.strip();
        if (!NUMERIC_ID_PATTERN.matcher(stripped).matches()) {
            throw new InputValidationException("INVALID_NUMERIC_ID", fieldName);
        }
        return Long.parseLong(stripped);
    }

    /**
     * Validate an email address.
     */
    public static String requireEmail(String value, String fieldName) {
        requireNonBlank(value, fieldName, 254);
        String stripped = value.strip().toLowerCase();
        if (!EMAIL_PATTERN.matcher(stripped).matches()) {
            throw new InputValidationException("INVALID_EMAIL", fieldName);
        }
        return stripped;
    }

    /**
     * Validate a phone number (E.164).
     */
    public static String requirePhone(String value, String fieldName) {
        requireNonBlank(value, fieldName, 16);
        String stripped = value.strip();
        if (!PHONE_PATTERN.matcher(stripped).matches()) {
            throw new InputValidationException("INVALID_PHONE", fieldName);
        }
        return stripped;
    }

    /**
     * Validate an optional field — returns null if blank, validated value otherwise.
     */
    public static String optionalSafeText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireSafeText(value, fieldName, maxLength);
    }

    /**
     * Validate an optional UUID — returns null if blank.
     */
    public static String optionalUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireUuid(value, fieldName);
    }

    /**
     * Strip HTML tags from a string. Does NOT make the string safe for
     * raw embedding in HTML — use for log-safe and DB-safe storage only.
     */
    public static String stripHtmlTags(String input) {
        if (input == null) return null;
        return input.replaceAll("<[^>]*>", "");
    }

    /**
     * Truncate a string to maxLength, appending "…" if truncated.
     */
    public static String truncate(String input, int maxLength) {
        Objects.requireNonNull(input);
        if (maxLength < 1) throw new IllegalArgumentException("maxLength must be >= 1");
        if (input.length() <= maxLength) return input;
        return input.substring(0, maxLength - 1) + "…";
    }

    // --- Internal helpers ---

    private static void rejectInjection(String value, String fieldName) {
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(value).find()) {
                throw new InputValidationException("INJECTION_DETECTED", fieldName);
            }
        }
    }
}
