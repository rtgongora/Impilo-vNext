package zw.gov.mohcc.impilo.governance.core;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Canonicalisation and validation of Health Service Commission EC numbers
 * (Channel B public-workforce matching key).
 *
 * <p>The EC number is a matching key, never an authenticator, so the shape
 * check is deliberately lenient: after canonicalisation (trim, uppercase,
 * strip internal whitespace) a valid EC number is 3–32 characters of
 * A-Z / 0-9 / '-' / '/' containing at least one digit.</p>
 */
public final class EcNumbers {

    private static final Pattern CANONICAL_PATTERN =
            Pattern.compile("^(?=.*[0-9])[A-Z0-9][A-Z0-9/\\-]{2,31}$");

    private EcNumbers() {
    }

    /**
     * Canonical form: trimmed, uppercased, internal whitespace removed.
     * Returns {@code null} for null/blank input.
     */
    public static String canonicalise(String raw) {
        if (raw == null) {
            return null;
        }
        String canonical = raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        return canonical.isEmpty() ? null : canonical;
    }

    /** True when the canonicalised value matches the accepted EC-number shape. */
    public static boolean isValid(String canonical) {
        return canonical != null && CANONICAL_PATTERN.matcher(canonical).matches();
    }

    /**
     * Privacy mask for logs, evidence refs, and API responses: first two
     * characters followed by {@code ***}. Never emit a raw EC number outside
     * the governance system of record.
     */
    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 2 ? trimmed + "***" : trimmed.substring(0, 2) + "***";
    }
}
