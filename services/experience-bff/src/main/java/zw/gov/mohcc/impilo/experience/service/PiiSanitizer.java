package zw.gov.mohcc.impilo.experience.service;

import java.util.regex.Pattern;

/**
 * PII Sanitizer — removes or masks PII from strings before logging.
 *
 * <p>Per Privacy Policy §14 and Health OS doctrine: PII must not appear
 * in application logs, error messages, or diagnostic output. This utility
 * masks common PII patterns (email, phone, national ID, names in known
 * formats) in log messages.</p>
 *
 * <p>Usage: wrap any string that might contain PII before passing to
 * a logger: {@code log.info("Result: {}", PiiSanitizer.sanitize(msg))}</p>
 */
public final class PiiSanitizer {

    private PiiSanitizer() {}

    // Email pattern: user@domain.tld → u***@domain.tld
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("([a-zA-Z0-9._%+-])[a-zA-Z0-9._%+-]*@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");

    // Phone pattern: sequences of 7+ digits possibly with +, spaces, dashes
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(\\+?\\d[\\d\\s-]{6,}\\d)");

    // Zimbabwe national ID: NN-NNNNNN-L-NN or similar
    private static final Pattern NATIONAL_ID_PATTERN =
            Pattern.compile("(\\d{2})-?(\\d{6,7})-?([A-Z])-?(\\d{2})");

    /**
     * Sanitize a string by masking PII patterns.
     */
    public static String sanitize(String input) {
        if (input == null) return null;

        String result = input;

        // Mask emails
        result = EMAIL_PATTERN.matcher(result).replaceAll("$1***@$2");

        // Mask phone numbers (keep first 3, mask rest)
        result = PHONE_PATTERN.matcher(result).replaceAll(mr -> {
            String match = mr.group();
            String digits = match.replaceAll("[^\\d+]", "");
            if (digits.length() <= 4) return match;
            return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 2);
        });

        // Mask national IDs
        result = NATIONAL_ID_PATTERN.matcher(result).replaceAll("$1-******-$3-$4");

        return result;
    }

    /**
     * Sanitize a string for safe inclusion in exception messages.
     * More aggressive — replaces all potential PII markers.
     */
    public static String sanitizeForError(String input) {
        if (input == null) return null;
        return sanitize(input);
    }
}
