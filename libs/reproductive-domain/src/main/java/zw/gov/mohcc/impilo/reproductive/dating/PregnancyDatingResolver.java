package zw.gov.mohcc.impilo.reproductive.dating;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides which dating basis a pregnancy runs on, and records the one it did not.
 *
 * <p>Pure and static: given the same bases and the same policy it returns the same answer forever,
 * which is what lets a dating decision made two years ago be re-explained against the rule that
 * applied at the time.
 *
 * <p>Two behaviours are the reason this class exists rather than a one-line "take the best method":
 *
 * <ul>
 *   <li><b>Precedence alone does not redate.</b> A more authoritative basis supersedes the current
 *       one only when the two disagree by more than the policy tolerance. Two methods that agree
 *       within a week are not in conflict, and rewriting the EDD to move it by three days churns
 *       every downstream schedule for no clinical gain.</li>
 *   <li><b>A rejected basis is still recorded.</b> {@link #revise} always returns a result, with
 *       {@code redatingApplied = false} when the candidate lost. An obstetric record must be able to
 *       say "we saw the 26-week scan and kept the LMP dating" — otherwise the next clinician sees a
 *       scan that appears to have been ignored.</li>
 * </ul>
 */
public final class PregnancyDatingResolver {

    private PregnancyDatingResolver() {
    }

    /**
     * Resolve dating from everything known. Returns null when no basis can produce a date — an
     * undated pregnancy is a real state and must not be rendered as a dated one.
     */
    public static PregnancyDating resolve(List<DatingBasis> bases, LocalDate asOf, RedatingPolicy policy) {
        List<DatingBasis> usable = new ArrayList<>();
        for (DatingBasis basis : bases == null ? List.<DatingBasis>of() : bases) {
            if (basis != null && basis.usable()) {
                usable.add(basis);
            }
        }
        if (usable.isEmpty()) {
            return null;
        }

        // Most authoritative first; where two share a method, the earlier observation wins, because
        // an earlier scan is the more precise one.
        usable.sort(Comparator
                .comparingInt((DatingBasis b) -> b.method().precedence())
                .thenComparing(b -> b.observedOn() == null ? LocalDate.MAX : b.observedOn()));

        DatingBasis adopted = usable.get(0);
        PregnancyDating dating = of(adopted, null, null, null, false, usable, asOf,
                "Dated from " + describe(adopted) + ".", policy);

        // Anything less authoritative is not a competitor, but a disagreement is still worth
        // surfacing on the first decision rather than only on later revisions.
        for (int i = 1; i < usable.size(); i++) {
            Integer discrepancy = EddCalculator.discrepancyDays(
                    adopted.estimatedDeliveryDate(), usable.get(i).estimatedDeliveryDate());
            if (discrepancy != null && discrepancy > 0) {
                dating = of(adopted, usable.get(i), discrepancy,
                        policy.toleranceDays(usable.get(i).method(), gestationAt(usable.get(i))),
                        false, usable, asOf,
                        "Dated from " + describe(adopted) + ". " + describe(usable.get(i))
                                + " gives an estimated delivery date " + discrepancy
                                + " day(s) different and does not supersede it.",
                        policy);
                break;
            }
        }
        return dating;
    }

    /**
     * Consider a new basis against existing dating.
     *
     * <p>Always returns a result, including when the candidate was rejected.
     */
    public static PregnancyDating revise(PregnancyDating current, DatingBasis candidate,
                                         LocalDate asOf, RedatingPolicy policy) {
        if (candidate == null || !candidate.usable()) {
            return current;
        }
        if (current == null) {
            return resolve(List.of(candidate), asOf, policy);
        }

        Integer discrepancy = EddCalculator.discrepancyDays(
                current.estimatedDeliveryDate(), candidate.estimatedDeliveryDate());
        Integer tolerance = policy.toleranceDays(candidate.method(), gestationAt(candidate));

        List<DatingBasis> considered = new ArrayList<>(current.consideredBases());
        considered.add(candidate);

        if (!supersedes(current.method(), candidate.method(), discrepancy, tolerance)) {
            return of(current.adoptedBasis(), candidate, discrepancy, tolerance, false,
                    considered, current.datedOn(),
                    rejectionRationale(current, candidate, discrepancy, tolerance), policy);
        }

        return of(candidate, current.adoptedBasis(), discrepancy, tolerance, true,
                considered, asOf,
                describe(candidate) + " supersedes " + describe(current.adoptedBasis())
                        + ": the two differ by " + discrepancy + " day(s), beyond the "
                        + tolerance + "-day tolerance for this method. Previous estimated delivery "
                        + "date " + current.estimatedDeliveryDate() + " retained.",
                policy);
    }

    /** Would this candidate change the dating? */
    public static boolean redatingIndicated(PregnancyDating current, DatingBasis candidate,
                                            RedatingPolicy policy) {
        if (current == null || candidate == null || !candidate.usable()) {
            return false;
        }
        return supersedes(current.method(), candidate.method(),
                EddCalculator.discrepancyDays(current.estimatedDeliveryDate(),
                        candidate.estimatedDeliveryDate()),
                policy.toleranceDays(candidate.method(), gestationAt(candidate)));
    }

    // ──────────────────────────────────────────────────────────────────────────

    private static boolean supersedes(DatingMethod incumbent, DatingMethod candidate,
                                      Integer discrepancy, Integer tolerance) {
        if (candidate == null || !candidate.usable() || discrepancy == null) {
            return false;
        }
        // A method the policy gives no tolerance to never redates, however far it disagrees. This is
        // what keeps a 30-week scan from redating a growth-restricted fetus into a normal one.
        if (tolerance == null) {
            return false;
        }
        if (incumbent != null && candidate.precedence() >= incumbent.precedence()) {
            return false;
        }
        return discrepancy > tolerance;
    }

    private static String rejectionRationale(PregnancyDating current, DatingBasis candidate,
                                             Integer discrepancy, Integer tolerance) {
        String head = describe(candidate) + " was considered and did not change the dating";
        if (tolerance == null) {
            return head + ": this method does not redate an established pregnancy. "
                    + "After about 22 weeks an ultrasound measures size rather than age, and "
                    + "redating on it would turn a small fetus into a younger normal one.";
        }
        if (candidate.method().precedence() >= current.method().precedence()) {
            return head + ": the current dating comes from " + describe(current.adoptedBasis())
                    + ", which is at least as authoritative.";
        }
        return head + ": it differs by " + discrepancy + " day(s), within the "
                + tolerance + "-day tolerance for this method.";
    }

    private static Integer gestationAt(DatingBasis basis) {
        if (basis.measuredGestationalAgeDays() != null) {
            return basis.measuredGestationalAgeDays();
        }
        return EddCalculator.gestationalAgeDays(basis.estimatedDeliveryDate(), basis.observedOn());
    }

    private static String describe(DatingBasis basis) {
        return switch (basis.method()) {
            case ASSISTED_CONCEPTION -> "assisted conception dating";
            case ULTRASOUND_CRL_FIRST_TRIMESTER -> "a first-trimester ultrasound";
            case ULTRASOUND_EARLY_SECOND_TRIMESTER -> "an early second-trimester ultrasound";
            case ULTRASOUND_LATE_SECOND_OR_THIRD -> "a late ultrasound";
            case LMP_CERTAIN -> "a certain last menstrual period";
            case LMP_UNCERTAIN -> "an uncertain last menstrual period";
            case SYMPHYSIS_FUNDAL_HEIGHT -> "symphysis-fundal height";
            case CLINICAL_ESTIMATE -> "a clinical estimate";
            case UNKNOWN -> "an unusable basis";
        };
    }

    private static PregnancyDating of(DatingBasis adopted, DatingBasis superseded, Integer discrepancy,
                                      Integer tolerance, boolean redated, List<DatingBasis> considered,
                                      LocalDate datedOn, String rationale, RedatingPolicy policy) {
        LocalDate edd = adopted.estimatedDeliveryDate();
        return new PregnancyDating(
                edd,
                EddCalculator.pregnancyStartDate(edd),
                adopted.method(),
                PregnancyDating.DatingConfidence.forMethod(adopted.method()),
                PregnancyDating.DatingConfidence.plusOrMinusDays(adopted.method()),
                datedOn,
                adopted,
                superseded,
                discrepancy,
                tolerance,
                redated,
                List.copyOf(considered),
                rationale,
                policy.contentVersion());
    }
}
