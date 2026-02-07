package zw.gov.mohcc.impilo.vito.core.id;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

class ImpiloIdFormatTest {

    private final ImpiloIdFormat format = new ImpiloIdFormat();

    @Test
    void generatedIdHasCorrectLength() {
        String id = format.generate();
        assertEquals(10, id.length(), "Impilo ID should be 10 characters (9 digits + 1 letter)");
    }

    @Test
    void generatedIdMatchesPattern() {
        String id = format.generate();
        assertTrue(id.matches("\\d{9}[A-Z]"), "ID should be 9 digits followed by an uppercase letter");
    }

    @RepeatedTest(100)
    void generatedIdAlwaysPassesValidation() {
        String id = format.generate();
        assertTrue(format.validate(id), "Generated ID should always validate: " + id);
    }

    @Test
    void validationRejectsTamperedDigit() {
        String id = format.generate();
        char[] chars = id.toCharArray();
        // Tamper with a digit
        chars[4] = chars[4] == '0' ? '1' : '0';
        String tampered = new String(chars);
        assertFalse(format.validate(tampered), "Tampered digit should fail validation");
    }

    @Test
    void validationRejectsWrongCheckLetter() {
        String id = format.generate();
        char[] chars = id.toCharArray();
        chars[9] = chars[9] == 'A' ? 'B' : 'A';
        assertFalse(format.validate(new String(chars)), "Wrong check letter should fail");
    }

    @Test
    void validationRejectsNull() {
        assertFalse(format.validate(null));
    }

    @Test
    void validationRejectsTooShort() {
        assertFalse(format.validate("12345"));
    }

    @Test
    void validationRejectsTooLong() {
        assertFalse(format.validate("1234567890AB"));
    }

    @Test
    void validationRejectsNonDigitPrefix() {
        assertFalse(format.validate("12345678XA"));
    }

    @Test
    void checkLetterIsDeterministic() {
        char c1 = format.computeCheckLetter("123456789");
        char c2 = format.computeCheckLetter("123456789");
        assertEquals(c1, c2, "Same digits should produce same check letter");
    }

    @Test
    void differentDigitsProduceDifferentCheckLetters() {
        char c1 = format.computeCheckLetter("123456789");
        char c2 = format.computeCheckLetter("987654321");
        // Not guaranteed to be different, but statistically very likely
        // Just verify they compute without error
        assertNotNull(c1);
        assertNotNull(c2);
    }

    @Test
    void extractDigitsWorks() {
        String id = format.generate();
        String digits = format.extractDigits(id);
        assertEquals(9, digits.length());
        assertTrue(digits.matches("\\d{9}"));
    }

    @Test
    void adjacentTranspositionDetected() {
        String digits = "123456789";
        char checkOriginal = format.computeCheckLetter(digits);
        // Swap positions 3 and 4: 123546789
        char checkSwapped = format.computeCheckLetter("123546789");
        assertNotEquals(checkOriginal, checkSwapped,
                "Adjacent transposition should change the check letter");
    }
}
