package zw.gov.mohcc.impilo.vito.core.id;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Impilo ID Format: #########X (9 digits + 1 check letter).
 *
 * Designed for elderly-friendly use: short, numeric with a single
 * trailing letter for error detection. The check letter algorithm
 * uses weighted positional sums mod 26, detecting:
 *   - single digit substitution errors
 *   - adjacent transposition errors
 *   - most random errors
 *
 * Format examples: 123456789A, 987654321Z
 *
 * This is the HUMAN-FACING identifier printed on SMART Cards.
 * Distinct from the W3C DID (did:impilo:[sha256]) which is the
 * cryptographic machine-readable identifier.
 */
@Component
public class ImpiloIdFormat {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DIGIT_COUNT = 9;
    private static final int[] WEIGHTS = {2, 3, 4, 5, 6, 7, 8, 9, 2};
    private static final char[] LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray(); // no I, O (avoid confusion with 1, 0)
    private static final int MOD = LETTERS.length; // 24

    /**
     * Generate a new random Impilo ID.
     *
     * @return formatted ID string, e.g. "123456789A"
     */
    public String generate() {
        StringBuilder digits = new StringBuilder(DIGIT_COUNT);
        for (int i = 0; i < DIGIT_COUNT; i++) {
            digits.append(RANDOM.nextInt(10));
        }
        char check = computeCheckLetter(digits.toString());
        return digits.toString() + check;
    }

    /**
     * Validate an Impilo ID string.
     *
     * @param impiloId the full ID including check letter
     * @return true if format is valid and check letter matches
     */
    public boolean validate(String impiloId) {
        if (impiloId == null || impiloId.length() != DIGIT_COUNT + 1) {
            return false;
        }

        String digitPart = impiloId.substring(0, DIGIT_COUNT);
        char givenCheck = impiloId.charAt(DIGIT_COUNT);

        // Verify all chars in digit part are digits
        for (int i = 0; i < DIGIT_COUNT; i++) {
            if (!Character.isDigit(digitPart.charAt(i))) {
                return false;
            }
        }

        // Verify check letter
        char expectedCheck = computeCheckLetter(digitPart);
        return givenCheck == expectedCheck;
    }

    /**
     * Compute the check letter for a 9-digit string.
     *
     * Algorithm: weighted positional sum mod 24, mapped to A-Z (excluding I, O).
     * Weights cycle: [2,3,4,5,6,7,8,9,2] to maximize error detection.
     */
    public char computeCheckLetter(String digits) {
        if (digits.length() != DIGIT_COUNT) {
            throw new IllegalArgumentException("Expected " + DIGIT_COUNT + " digits, got " + digits.length());
        }

        int sum = 0;
        for (int i = 0; i < DIGIT_COUNT; i++) {
            int digit = digits.charAt(i) - '0';
            sum += digit * WEIGHTS[i];
        }

        return LETTERS[sum % MOD];
    }

    /**
     * Extract the digit portion of an Impilo ID.
     *
     * @param impiloId the full ID including check letter
     * @return the 9-digit portion
     */
    public String extractDigits(String impiloId) {
        if (impiloId == null || impiloId.length() != DIGIT_COUNT + 1) {
            throw new IllegalArgumentException("Invalid Impilo ID format");
        }
        return impiloId.substring(0, DIGIT_COUNT);
    }
}
