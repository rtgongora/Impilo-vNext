package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Score-or-reason discipline for cognition/mood screens (V117).
 * Both present is invalid; both absent means unassessed (allowed).
 */
class ClerkingExtensionServiceTest {

    static boolean validScoreOrReason(String score, String absentReason) {
        boolean hasScore = score != null && !score.isBlank();
        boolean hasAbsent = absentReason != null && !absentReason.isBlank();
        return !(hasScore && hasAbsent);
    }

    @Test
    void scoreXorAbsentReason() {
        assertTrue(validScoreOrReason("2", null));
        assertTrue(validScoreOrReason(null, "too unwell"));
        assertTrue(validScoreOrReason(null, null));
        assertFalse(validScoreOrReason("2", "too unwell"));
    }
}
