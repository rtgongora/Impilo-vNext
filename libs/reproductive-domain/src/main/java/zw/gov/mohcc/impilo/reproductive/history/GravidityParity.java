package zw.gov.mohcc.impilo.reproductive.history;

import java.util.List;

/**
 * Gravidity and parity, derived from structured pregnancy history.
 *
 * <p>Every component is nullable and a null renders as "?", never as zero. That is the whole point:
 * a woman whose history could not be counted is not a nullipara, and "G? P?" prompts someone to ask
 * while "G0 P0" reads as an answered question. Grand multiparity is one of the strongest predictors
 * of postpartum haemorrhage, so a history quietly counted as zero removes a risk factor rather than
 * reporting an unknown.
 *
 * @param para            counts PREGNANCIES carried to viability, not babies. Twins delivered at
 *                        term are para 1.
 * @param living          counts CHILDREN currently alive. The same twins are living 2. Getting
 *                        these two the wrong way round overstates parity and mis-stratifies risk.
 * @param complete        false when any entry could not be counted; the components are still
 *                        reported, over what was countable
 * @param uncountableReasons one line per entry that could not be counted, naming why
 */
public record GravidityParity(
        Integer gravida,
        Integer para,
        Integer abortus,
        Integer term,
        Integer preterm,
        Integer living,
        int entriesCounted,
        int entriesUncountable,
        List<String> uncountableReasons,
        boolean complete,
        String contentVersion) {

    /** "G4 P2 A1", with "?" for anything that could not be derived. */
    public String gpaDisplay() {
        return "G" + show(gravida) + " P" + show(para) + " A" + show(abortus);
    }

    /** "4-2-1-3" — term, preterm, abortions, living. */
    public String tpalDisplay() {
        return show(term) + "-" + show(preterm) + "-" + show(abortus) + "-" + show(living);
    }

    /**
     * Five or more previous births. Null-safe and deliberately false when parity is unknown — this
     * is a risk flag, and a flag raised from an unknown is a flag that fires on everyone.
     * Incompleteness is reported through {@link #complete()}, not by guessing here.
     */
    public boolean grandMultipara() {
        return para != null && para >= 5;
    }

    /** True when a clinician should be asked to confirm the history rather than trust the count. */
    public boolean needsVerification() {
        return !complete || gravida == null || para == null;
    }

    private static String show(Integer value) {
        return value == null ? "?" : String.valueOf(value);
    }

    /**
     * Derived versus what the woman or clinician stated. Discrepancies are surfaced, never silently
     * reconciled: a self-reported G5 against two recorded pregnancies usually means three
     * pregnancies happened elsewhere, and overwriting either number loses that.
     */
    public record Reconciliation(boolean agrees, List<String> discrepancies) {
    }
}
