package zw.gov.mohcc.impilo.vito.core.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Normative test vectors for Impilo ID format v2 (Identity Contract §4.1 —
 * ISO 7064 MOD 11,10). These vectors are published in the contract; changing
 * them is a format-version event, never a silent edit.
 */
@DisplayName("ImpiloIdFormatV2 (ISO 7064 MOD 11,10)")
class ImpiloIdFormatV2Test {

    private final ImpiloIdFormatV2 format = new ImpiloIdFormatV2();

    @ParameterizedTest
    @CsvSource({
            "123456789, 7",
            "000000000, 6",
            "987654321, 3",
            "555019273, 7",
            "400072186, 6",
    })
    @DisplayName("contract test vectors produce the published check digits")
    void contractVectors(String body, int expectedCheck) {
        assertEquals(expectedCheck, format.computeCheckDigit(body));
        assertTrue(format.validate(body + expectedCheck));
    }

    @Test
    @DisplayName("wrong check digit fails validation")
    void wrongCheckDigit_fails() {
        assertFalse(format.validate("1234567890"));
    }

    @Test
    @DisplayName("detects every single-digit substitution and adjacent transposition (contract guarantee)")
    void errorDetection_exhaustiveOnVector() {
        String body = "123456789";
        String full = body + format.computeCheckDigit(body);
        for (int i = 0; i < 9; i++) {
            for (char d = '0'; d <= '9'; d++) {
                if (d == body.charAt(i)) continue;
                String mutated = body.substring(0, i) + d + body.substring(i + 1);
                assertFalse(format.validate(mutated + full.charAt(9)),
                        "substitution at " + i + " must be detected");
            }
        }
        for (int i = 0; i < 8; i++) {
            char[] t = body.toCharArray();
            char tmp = t[i]; t[i] = t[i + 1]; t[i + 1] = tmp;
            String swapped = new String(t);
            if (swapped.equals(body)) continue;
            assertFalse(format.validate(swapped + full.charAt(9)),
                    "transposition at " + i + " must be detected");
        }
    }

    @Test
    @DisplayName("normalize strips display grouping; display renders 3-3-3-1")
    void normalizeAndDisplay() {
        assertTrue(format.validate("123 456 789 7"));
        assertTrue(format.validate("123-456-789-7"));
        assertEquals("123 456 789 7", format.display("1234567897"));
    }

    @Test
    @DisplayName("generated IDs are 10 digits and self-validate")
    void generate_validates() {
        for (int i = 0; i < 500; i++) {
            String id = format.generate();
            assertEquals(10, id.length());
            assertTrue(format.validate(id), "generated ID must validate: " + id);
        }
    }

    @Test
    @DisplayName("rejects nulls, short, long, and non-numeric values")
    void rejectsMalformed() {
        assertFalse(format.validate(null));
        assertFalse(format.validate("123456789"));
        assertFalse(format.validate("12345678901"));
        assertFalse(format.validate("12345678xA"));
    }
}
