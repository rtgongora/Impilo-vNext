package zw.gov.mohcc.impilo.reproductive.history;

import java.time.LocalDate;

/**
 * One previous pregnancy, as structured data rather than a sentence in a free-text history.
 *
 * @param birthCount          fetuses delivered — twins are 2. Distinct from parity, which counts
 *                            the pregnancy once.
 * @param survivingChildCount children from this pregnancy alive NOW, which is what "living" in a
 *                            GPA count means. A live birth followed by a neonatal death is a live
 *                            birth with no surviving child, and collapsing the two loses the death.
 */
public record PregnancyHistoryEntry(
        String pregnancyEpisodeRef,
        PregnancyOutcome outcome,
        Integer gestationalAgeDaysAtEnd,
        Integer birthCount,
        Integer liveBirthCount,
        Integer survivingChildCount,
        LocalDate endedOn) {

    public static PregnancyHistoryEntry liveBirth(LocalDate endedOn, int gestationalAgeDays,
                                                  int babies, int surviving) {
        return new PregnancyHistoryEntry(null, PregnancyOutcome.LIVE_BIRTH, gestationalAgeDays,
                babies, babies, surviving, endedOn);
    }

    public static PregnancyHistoryEntry miscarriage(LocalDate endedOn, Integer gestationalAgeDays) {
        return new PregnancyHistoryEntry(null, PregnancyOutcome.MISCARRIAGE, gestationalAgeDays,
                0, 0, 0, endedOn);
    }
}
