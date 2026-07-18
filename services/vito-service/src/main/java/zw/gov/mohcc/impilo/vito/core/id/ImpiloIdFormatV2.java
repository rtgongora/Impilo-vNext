package zw.gov.mohcc.impilo.vito.core.id;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Impilo ID format v2 — the ratified national issuance format
 * (Identity Contract §4.1, decision D8).
 *
 * <p>Structure: {@code DDDDDDDDDC} — 9 random digits + 1 <b>ISO 7064 MOD 11,10</b>
 * check digit. All-numeric for verbal relay, phone keypads, USSD and
 * elderly-friendly transcription; detects 100% of single-character substitutions
 * and 100% of adjacent transpositions. No semantic encoding (no facility,
 * province, birth year, sex, or issuance date is derivable).</p>
 *
 * <p>Display grouping {@code 123 456 789 7}; {@link #normalize} strips
 * whitespace/hyphens before validation. Format v1 ({@code #########X} weighted
 * mod-24 check letter, {@link ImpiloIdFormat}) is retired for issuance and
 * retained only to validate pre-v2 values; legacy MRS PHIDs
 * ({@link LegacyPhidFormat}) remain permanently valid aliases.</p>
 *
 * <p>Normative test vectors (Identity Contract §4.1): {@code 123456789→1234567897},
 * {@code 000000000→0000000006}, {@code 987654321→9876543213},
 * {@code 555019273→5550192737}, {@code 400072186→4000721866}.</p>
 */
@Component
public class ImpiloIdFormatV2 {

    /** Format-version tag persisted on issuance records. */
    public static final int FORMAT_VERSION = 2;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BODY_DIGITS = 9;
    private static final int TOTAL_LENGTH = BODY_DIGITS + 1;

    /** Generate a new random v2 Impilo ID (10 digits, last is the check digit). */
    public String generate() {
        StringBuilder body = new StringBuilder(BODY_DIGITS);
        for (int i = 0; i < BODY_DIGITS; i++) {
            body.append(RANDOM.nextInt(10));
        }
        return body + Integer.toString(computeCheckDigit(body.toString()));
    }

    /** Strip whitespace and hyphens (display grouping) before validation. */
    public String normalize(String raw) {
        return raw == null ? null : raw.replaceAll("[\\s-]", "");
    }

    /** Validate a normalized-or-raw v2 Impilo ID including its check digit. */
    public boolean validate(String impiloId) {
        String id = normalize(impiloId);
        if (id == null || id.length() != TOTAL_LENGTH) {
            return false;
        }
        for (int i = 0; i < TOTAL_LENGTH; i++) {
            if (!Character.isDigit(id.charAt(i))) {
                return false;
            }
        }
        return computeCheckDigit(id.substring(0, BODY_DIGITS)) == id.charAt(BODY_DIGITS) - '0';
    }

    /**
     * ISO 7064 MOD 11,10 (hybrid system) check digit over the 9-digit body.
     *
     * <pre>
     * p ← 10
     * for each digit d, left to right:
     *     s ← (d + p) mod 10;  if s = 0 then s ← 10
     *     p ← (2·s) mod 11
     * check ← 11 − p;  if check = 10 then check ← 0
     * </pre>
     */
    public int computeCheckDigit(String body) {
        if (body == null || body.length() != BODY_DIGITS) {
            throw new IllegalArgumentException("Expected " + BODY_DIGITS + " digits");
        }
        int p = 10;
        for (int i = 0; i < BODY_DIGITS; i++) {
            int d = body.charAt(i) - '0';
            int s = (d + p) % 10;
            if (s == 0) {
                s = 10;
            }
            p = (2 * s) % 11;
        }
        int check = 11 - p;
        return check == 10 ? 0 : check;
    }

    /** Display form with 3-3-3-1 grouping, e.g. {@code 123 456 789 7}. */
    public String display(String impiloId) {
        String id = normalize(impiloId);
        if (id == null || id.length() != TOTAL_LENGTH) {
            return impiloId;
        }
        return id.substring(0, 3) + " " + id.substring(3, 6) + " "
                + id.substring(6, 9) + " " + id.charAt(9);
    }
}
