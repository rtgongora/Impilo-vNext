package zw.gov.mohcc.impilo.reproductive.history;

import zw.gov.mohcc.impilo.reproductive.stage.BirthMaturityBand;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives gravidity and parity from structured pregnancy outcomes.
 *
 * <p>Three rules that are easy to state and are got wrong constantly:
 *
 * <ol>
 *   <li><b>Para counts pregnancies, not babies.</b> Twins delivered at term are para 1 and living 2.
 *       Counting babies inflates parity, and parity drives risk stratification — grand multiparity
 *       is a haemorrhage risk factor, so an inflated count raises a flag that should not be raised
 *       and a deflated one hides a real one.</li>
 *   <li><b>A live birth of unknown gestation is uncountable for term versus preterm.</b> It is
 *       reported as such rather than binned into term, because "we do not know when her last baby
 *       came" is the fact, and quietly calling it term erases a previous-preterm-birth risk
 *       factor.</li>
 *   <li><b>An incomplete history is not silently completed.</b> The count is reported over what was
 *       countable and says how much it could not count.</li>
 * </ol>
 */
public final class GravidityParityCalculator {

    public static final String CONTENT_VERSION = "impilo-gravidity-parity-1.0.0";

    private GravidityParityCalculator() {
    }

    /**
     * Derive from history. Never returns null; components are null where the history cannot support
     * them, and every skipped entry is named.
     *
     * @param includeCurrentPregnancy true when the woman is pregnant now — gravidity counts the
     *                                current pregnancy, parity does not
     */
    public static GravidityParity derive(List<PregnancyHistoryEntry> history,
                                         LossThresholdPolicy policy,
                                         boolean includeCurrentPregnancy) {
        List<PregnancyHistoryEntry> entries =
                history == null ? List.of() : history.stream().filter(e -> e != null).toList();

        List<String> uncountable = new ArrayList<>();
        int counted = 0;
        int gravida = includeCurrentPregnancy ? 1 : 0;
        int para = 0;
        int abortus = 0;
        int term = 0;
        int preterm = 0;
        int living = 0;
        boolean paraKnown = true;
        boolean termSplitKnown = true;
        boolean livingKnown = true;

        for (PregnancyHistoryEntry entry : entries) {
            if (entry.outcome() == null || entry.outcome() == PregnancyOutcome.UNKNOWN) {
                uncountable.add(describe(entry) + ": outcome not recorded, so it counts toward "
                        + "gravidity but cannot be classified as a birth or a loss");
                gravida++;
                paraKnown = false;
                continue;
            }
            if (entry.outcome() == PregnancyOutcome.ONGOING) {
                // The current pregnancy, arriving through the history list rather than the flag.
                gravida++;
                continue;
            }

            gravida++;
            counted++;

            if (entry.outcome().reachedViability(entry.gestationalAgeDaysAtEnd(), policy)) {
                // One pregnancy, one increment, however many babies it produced.
                para++;
                BirthMaturityBand band = BirthMaturityBand.ofGestationalAgeDays(entry.gestationalAgeDaysAtEnd());
                if (band == null) {
                    uncountable.add(describe(entry) + ": gestation at delivery not recorded, so it "
                            + "cannot be counted as term or preterm");
                    termSplitKnown = false;
                } else if (band.preterm()) {
                    preterm++;
                } else {
                    term++;
                }
            } else {
                abortus++;
            }

            if (entry.survivingChildCount() == null) {
                livingKnown = false;
            } else {
                living += entry.survivingChildCount();
            }
        }

        boolean complete = uncountable.isEmpty();
        return new GravidityParity(
                gravida,
                paraKnown ? para : null,
                paraKnown ? abortus : null,
                termSplitKnown && paraKnown ? term : null,
                termSplitKnown && paraKnown ? preterm : null,
                livingKnown ? living : null,
                counted,
                uncountable.size(),
                List.copyOf(uncountable),
                complete,
                CONTENT_VERSION);
    }

    /**
     * Compare a derived count with what was stated. A woman's own account of her pregnancies is
     * evidence, not noise — she is usually the only person who was present for all of them.
     */
    public static GravidityParity.Reconciliation reconcile(GravidityParity derived,
                                                           Integer gravidaRecorded,
                                                           Integer paraRecorded) {
        List<String> discrepancies = new ArrayList<>();
        if (gravidaRecorded != null && derived.gravida() != null
                && !gravidaRecorded.equals(derived.gravida())) {
            discrepancies.add("She reports " + gravidaRecorded + " pregnancies; "
                    + derived.gravida() + " are recorded here. Pregnancies cared for elsewhere are "
                    + "the usual explanation.");
        }
        if (paraRecorded != null && derived.para() != null && !paraRecorded.equals(derived.para())) {
            discrepancies.add("She reports para " + paraRecorded + "; " + derived.para()
                    + " is derived from the recorded outcomes.");
        }
        return new GravidityParity.Reconciliation(discrepancies.isEmpty(), List.copyOf(discrepancies));
    }

    private static String describe(PregnancyHistoryEntry entry) {
        if (entry.endedOn() != null) {
            return "pregnancy ending " + entry.endedOn();
        }
        return entry.pregnancyEpisodeRef() == null ? "an undated pregnancy"
                : "pregnancy " + entry.pregnancyEpisodeRef();
    }
}
