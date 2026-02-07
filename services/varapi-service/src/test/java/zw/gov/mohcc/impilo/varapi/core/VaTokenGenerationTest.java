package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests VA token generation logic and Luhn check digit computation.
 *
 * <p>VA tokens follow the format {@code VA-XXXXXXXXXC} where X is a random
 * digit (10 digits total) and C is the Luhn check digit appended at the end,
 * yielding a 14-character string: {@code VA-} prefix (3) + 10 digits + 1 check
 * digit = 14 characters.</p>
 *
 * <p>The generation and validation helpers are implemented as package-private
 * static methods here, mirroring the logic that a production {@code TokenService}
 * would use.</p>
 */
class VaTokenGenerationTest {

    private static final String PREFIX = "VA-";
    private static final int DIGIT_COUNT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    // -- Token generation helpers (mirrors TokenService logic) --

    /**
     * Generate a VA token: {@code VA-} + 10 random digits + 1 Luhn check digit.
     */
    static String generateVaToken() {
        StringBuilder digits = new StringBuilder(DIGIT_COUNT);
        for (int i = 0; i < DIGIT_COUNT; i++) {
            digits.append(RANDOM.nextInt(10));
        }
        char check = computeLuhnCheck(digits.toString());
        return PREFIX + digits + check;
    }

    /**
     * Compute the Luhn check digit for a string of digits.
     *
     * <p>Uses the standard Luhn mod-10 algorithm: starting from the rightmost
     * digit, double every second digit; if the result exceeds 9, subtract 9;
     * sum all digits; the check digit is {@code (10 - (sum % 10)) % 10}.</p>
     */
    static char computeLuhnCheck(String digits) {
        int sum = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            // Double every digit at an even position from the right (0-indexed from right)
            int posFromRight = digits.length() - 1 - i;
            if (posFromRight % 2 == 0) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
        }
        int check = (10 - (sum % 10)) % 10;
        return (char) ('0' + check);
    }

    /**
     * Validate the Luhn check digit of a complete token payload (digits + check).
     */
    static boolean validateLuhn(String digitsPlusCheck) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digitsPlusCheck.length() - 1; i >= 0; i--) {
            int d = digitsPlusCheck.charAt(i) - '0';
            if (doubleDigit) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleDigit = !doubleDigit;
        }
        return (sum % 10) == 0;
    }

    /**
     * Normalize a VA token: remove dashes and spaces, uppercase.
     */
    static String normalize(String token) {
        if (token == null) return null;
        return token.replaceAll("[\\s-]", "").toUpperCase();
    }

    // -- Tests --

    @Test
    void generatedToken_hasCorrectFormat() {
        String token = generateVaToken();
        assertNotNull(token);
        assertTrue(token.startsWith(PREFIX), "Token should start with 'VA-'");
        assertEquals(14, token.length(), "Token should be 14 characters: VA- + 10 digits + 1 check digit");
    }

    @Test
    void generatedToken_digitsAreNumeric() {
        String token = generateVaToken();
        String payload = token.substring(3); // Remove "VA-"
        assertTrue(payload.matches("\\d{11}"),
                "Payload after prefix should be exactly 11 digits (10 random + 1 check)");
    }

    @Test
    void generatedToken_passesLuhnValidation() {
        String token = generateVaToken();
        String payload = token.substring(3); // 10 digits + check
        assertTrue(validateLuhn(payload),
                "Generated token payload should pass Luhn validation: " + payload);
    }

    @RepeatedTest(50)
    void generatedToken_alwaysPassesLuhnValidation() {
        String token = generateVaToken();
        String payload = token.substring(3);
        assertTrue(validateLuhn(payload),
                "Token must always pass Luhn check: " + token);
    }

    @Test
    void luhnCheck_knownValue_7992739871() {
        // Well-known test vector: Luhn of "7992739871" = 3
        assertEquals('3', computeLuhnCheck("7992739871"));
    }

    @Test
    void luhnCheck_knownValue_allZeros() {
        // "0000000000" -> all sums are 0, check = (10 - 0) % 10 = 0
        assertEquals('0', computeLuhnCheck("0000000000"));
    }

    @Test
    void luhnCheck_detectsSingleDigitError() {
        String digits = "1234567890";
        char check = computeLuhnCheck(digits);
        // Flip one digit
        String modified = "1234567891";
        char check2 = computeLuhnCheck(modified);
        assertNotEquals(check, check2,
                "Single digit change should produce a different check digit");
    }

    @Test
    void luhnCheck_detectsAdjacentTransposition() {
        String digits = "1234567890";
        char check = computeLuhnCheck(digits);
        // Swap adjacent digits at positions 4 and 5: "1234657890"
        String transposed = "1234657890";
        char checkT = computeLuhnCheck(transposed);
        assertNotEquals(check, checkT,
                "Adjacent transposition should change the check digit");
    }

    @Test
    void normalize_removesSpacesAndDashes() {
        assertEquals("VA1234567890", normalize("VA-123 456 7890"));
    }

    @Test
    void normalize_uppercasesInput() {
        assertEquals("VA1234567890", normalize("va-1234567890"));
    }

    @Test
    void normalize_handlesNull() {
        assertNull(normalize(null));
    }

    @Test
    void normalize_handlesExtraneousWhitespace() {
        assertEquals("VA1234567890", normalize("  VA - 1234 5678 90 "));
    }

    @Test
    void generatedTokens_areUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(generateVaToken());
        }
        assertEquals(1000, tokens.size(),
                "1000 generated tokens should all be unique");
    }

    @Test
    void luhnValidation_rejectsCorruptedPayload() {
        String token = generateVaToken();
        String payload = token.substring(3);
        // Corrupt the first digit
        char first = payload.charAt(0);
        char corrupted = first == '0' ? '1' : '0';
        String badPayload = corrupted + payload.substring(1);
        assertFalse(validateLuhn(badPayload),
                "Corrupted payload should fail Luhn validation");
    }

    @Test
    void checkDigit_isDeterministic() {
        String digits = "5551234567";
        char c1 = computeLuhnCheck(digits);
        char c2 = computeLuhnCheck(digits);
        assertEquals(c1, c2, "Same input should always produce the same check digit");
    }
}
