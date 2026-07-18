package zw.gov.mohcc.impilo.vito.core.proofing.engine;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default {@link DocumentVerificationEngine}: a real native document-integrity check —
 * ICAO 9303 machine-readable-zone (MRZ) check-digit validation for TD3 passports (2×44)
 * and TD1 identity cards (3×30). This is deterministic and needs no vendor SDK, so
 * document proofing has a genuine native capability from day one (native-first doctrine).
 *
 * <p><b>Scope honesty:</b> valid check digits prove the MRZ is internally consistent and
 * not mis-keyed — they do <b>not</b> prove the document is authentic (no forensic tamper
 * / hologram / UV analysis) nor that it matches an authoritative register (that is the
 * CRVS/RG link). So a positive result is {@link ProofingSignal#VERIFIED} at a capped
 * confidence, and downstream assurance must not treat it as authoritative on its own. A
 * failed check digit is a hard {@link ProofingSignal#REJECTED} (the number was altered or
 * mistyped). Absent/unsupported MRZ yields {@link ProofingSignal#INCONCLUSIVE} so the
 * orchestrator escalates to an assisted / vendor-forensic / in-person path.</p>
 */
@Component
public class NativeDocumentVerificationEngine implements DocumentVerificationEngine {

    /** MRZ format integrity is corroborating, not authoritative — cap the confidence. */
    private static final double MRZ_INTEGRITY_CONFIDENCE = 0.6;

    private static final int[] WEIGHTS = {7, 3, 1};

    @Override
    public ProofingSignal verifyDocument(DocumentEvidence evidence) {
        if (evidence == null) {
            return ProofingSignal.invalidInput(CAPABILITY, "No document evidence supplied");
        }
        if (!evidence.hasMrz()) {
            return ProofingSignal.inconclusive(CAPABILITY,
                    "No MRZ present — native integrity check cannot decide; escalate to assisted/vendor forensics");
        }

        List<String> lines = evidence.mrzLines();
        try {
            if (lines.size() == 2) {
                return verifyTd3(lines);
            }
            if (lines.size() == 3) {
                return verifyTd1(lines);
            }
            return ProofingSignal.inconclusive(CAPABILITY,
                    "Unsupported MRZ line count " + lines.size() + " (expected 2 for TD3 or 3 for TD1)");
        } catch (RuntimeException e) {
            // Malformed MRZ (wrong length / bad chars) — cannot validate, do not fabricate a pass.
            return ProofingSignal.invalidInput(CAPABILITY, "Malformed MRZ: " + e.getMessage());
        }
    }

    /** TD3 passport MRZ (two 44-char lines): validate document, DOB, expiry and composite check digits. */
    private ProofingSignal verifyTd3(List<String> lines) {
        String l2 = pad(lines.get(1), 44);

        String docNumber = l2.substring(0, 9);
        char docCheck = l2.charAt(9);
        String dob = l2.substring(13, 19);
        char dobCheck = l2.charAt(19);
        String expiry = l2.substring(21, 27);
        char expiryCheck = l2.charAt(27);
        String optional = l2.substring(28, 42);
        char optionalCheck = l2.charAt(42);
        char composite = l2.charAt(43);

        if (!digitMatches(docNumber, docCheck)) {
            return ProofingSignal.rejected(CAPABILITY, "Document-number check digit failed (TD3)");
        }
        if (!digitMatches(dob, dobCheck)) {
            return ProofingSignal.rejected(CAPABILITY, "Date-of-birth check digit failed (TD3)");
        }
        if (!digitMatches(expiry, expiryCheck)) {
            return ProofingSignal.rejected(CAPABILITY, "Expiry-date check digit failed (TD3)");
        }
        String compositeInput = docNumber + docCheck + dob + dobCheck + expiry + expiryCheck + optional + optionalCheck;
        if (!digitMatches(compositeInput, composite)) {
            return ProofingSignal.rejected(CAPABILITY, "Composite check digit failed (TD3)");
        }
        return ProofingSignal.verified(CAPABILITY, MRZ_INTEGRITY_CONFIDENCE,
                "TD3 MRZ check digits valid (format integrity only — not document authenticity)");
    }

    /** TD1 identity-card MRZ (three 30-char lines): validate document, DOB, expiry and composite check digits. */
    private ProofingSignal verifyTd1(List<String> lines) {
        String l1 = pad(lines.get(0), 30);
        String l2 = pad(lines.get(1), 30);

        String docNumber = l1.substring(5, 14);
        char docCheck = l1.charAt(14);
        String dob = l2.substring(0, 6);
        char dobCheck = l2.charAt(6);
        String expiry = l2.substring(8, 14);
        char expiryCheck = l2.charAt(14);
        char composite = l2.charAt(29);

        if (!digitMatches(docNumber, docCheck)) {
            return ProofingSignal.rejected(CAPABILITY, "Document-number check digit failed (TD1)");
        }
        if (!digitMatches(dob, dobCheck)) {
            return ProofingSignal.rejected(CAPABILITY, "Date-of-birth check digit failed (TD1)");
        }
        if (!digitMatches(expiry, expiryCheck)) {
            return ProofingSignal.rejected(CAPABILITY, "Expiry-date check digit failed (TD1)");
        }
        // TD1 composite runs over l1[5..30] + l2[0..7] + l2[8..15] + l2[18..29].
        String compositeInput = l1.substring(5, 30) + l2.substring(0, 7) + l2.substring(8, 15) + l2.substring(18, 29);
        if (!digitMatches(compositeInput, composite)) {
            return ProofingSignal.rejected(CAPABILITY, "Composite check digit failed (TD1)");
        }
        return ProofingSignal.verified(CAPABILITY, MRZ_INTEGRITY_CONFIDENCE,
                "TD1 MRZ check digits valid (format integrity only — not document authenticity)");
    }

    private static boolean digitMatches(String field, char check) {
        // A filler check digit ('<') is only valid over an all-filler field.
        int expected = computeCheckDigit(field);
        if (check == '<') {
            return field.chars().allMatch(c -> c == '<');
        }
        if (check < '0' || check > '9') {
            return false;
        }
        return expected == (check - '0');
    }

    static int computeCheckDigit(String field) {
        int sum = 0;
        for (int i = 0; i < field.length(); i++) {
            sum += charValue(field.charAt(i)) * WEIGHTS[i % 3];
        }
        return sum % 10;
    }

    private static int charValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        }
        if (c == '<') {
            return 0;
        }
        throw new IllegalArgumentException("Illegal MRZ character '" + c + "'");
    }

    private static String pad(String line, int len) {
        String s = line == null ? "" : line.trim().toUpperCase().replace(' ', '<');
        if (s.length() > len) {
            return s.substring(0, len);
        }
        StringBuilder b = new StringBuilder(s);
        while (b.length() < len) {
            b.append('<');
        }
        return b.toString();
    }
}
