package zw.gov.mohcc.impilo.vito.core.id;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Set;

/**
 * Legacy 8-character MRS PHID format: {@code ^[0-9]{3}[A-HJ-NP-Z][0-9]{3}[0-9]$}
 *
 * Structure: DDD L DDD C
 *   D = random digit (0-9)
 *   L = random letter, excluding I and O (avoid confusion with 1 and 0)
 *   C = check digit
 *
 * Check digit algorithm: sum the 6 digit characters at positions 0,1,2,4,5,6,
 * then compute {@code (10 - (total % 10)) % 10}.
 *
 * Example: "123A4560" — digits 1,2,3,4,5,6 → sum=21 → check=(10-(21%10))%10=9 → "123A4569"
 */
@Component
public class LegacyPhidFormat {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final Set<Character> LETTER_SET;

    static {
        LETTER_SET = new java.util.HashSet<>();
        for (char c : LETTERS) {
            LETTER_SET.add(c);
        }
    }

    private static final int[] DIGIT_POSITIONS = {0, 1, 2, 4, 5, 6};
    private static final int LETTER_POSITION = 3;
    private static final int CHECK_POSITION = 7;
    private static final int TOTAL_LENGTH = 8;

    public String generate() {
        int[] digits = new int[6];
        for (int i = 0; i < digits.length; i++) {
            digits[i] = RANDOM.nextInt(10);
        }
        char letter = LETTERS[RANDOM.nextInt(LETTERS.length)];
        int check = computeCheckDigit(digits);

        return "" + digits[0] + digits[1] + digits[2] + letter + digits[3] + digits[4] + digits[5] + check;
    }

    public boolean validate(String phid) {
        if (phid == null || phid.length() != TOTAL_LENGTH) {
            return false;
        }

        for (int pos : DIGIT_POSITIONS) {
            if (!Character.isDigit(phid.charAt(pos))) {
                return false;
            }
        }

        char letter = phid.charAt(LETTER_POSITION);
        if (!LETTER_SET.contains(letter)) {
            return false;
        }

        if (!Character.isDigit(phid.charAt(CHECK_POSITION))) {
            return false;
        }

        int[] digits = extractDigits(phid);
        int expectedCheck = computeCheckDigit(digits);
        int actualCheck = phid.charAt(CHECK_POSITION) - '0';
        return expectedCheck == actualCheck;
    }

    private int[] extractDigits(String phid) {
        int[] digits = new int[6];
        for (int i = 0; i < DIGIT_POSITIONS.length; i++) {
            digits[i] = phid.charAt(DIGIT_POSITIONS[i]) - '0';
        }
        return digits;
    }

    private int computeCheckDigit(int[] digits) {
        int total = 0;
        for (int d : digits) {
            total += d;
        }
        return (10 - (total % 10)) % 10;
    }
}
