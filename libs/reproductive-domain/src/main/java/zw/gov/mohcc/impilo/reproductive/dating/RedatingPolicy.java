package zw.gov.mohcc.impilo.reproductive.dating;

/**
 * How far two dating methods may disagree before the more precise one supersedes the other.
 *
 * <p>These tolerances are a clinical policy judgement, not arithmetic, so they are injected rather
 * than compiled in. A ministry that adopts different thresholds should be able to change them
 * without a code release — the same reason the immunisation schedule is content and not constants.
 *
 * <p>An engineering seed ships so the library still runs offline and in a unit test, and it says so
 * in its own version string. It is not a ratified national protocol.
 */
public interface RedatingPolicy {

    /**
     * Days of discrepancy beyond which {@code candidate} supersedes the current dating, given the
     * gestation at which the candidate was measured. Null means this method never redates.
     */
    Integer toleranceDays(DatingMethod candidate, Integer gestationalAgeDaysAtMeasurement);

    String contentVersion();

    String approvalStatus();

    static RedatingPolicy engineeringSeed() {
        return new EngineeringSeed();
    }

    /**
     * Widely used obstetric practice: an early scan overrides a certain LMP on a discrepancy of more
     * than about a week, the window widens as the scan gets later and less precise, and past 22
     * weeks a scan does not redate at all.
     *
     * <p>That last one is the clinically load-bearing rule. After 22 weeks a scan measures size, and
     * redating a small fetus onto its own measurements turns growth restriction into a normal
     * younger pregnancy — the finding disappears at exactly the moment it starts to matter.
     */
    final class EngineeringSeed implements RedatingPolicy {

        private static final int FIRST_TRIMESTER_TOLERANCE_DAYS = 7;
        private static final int EARLY_SECOND_TRIMESTER_TOLERANCE_DAYS = 10;
        private static final int LATE_SCAN_GESTATION_DAYS = 22 * 7;

        @Override
        public Integer toleranceDays(DatingMethod candidate, Integer gestationalAgeDaysAtMeasurement) {
            if (candidate == null || !candidate.usable()) {
                return null;
            }
            return switch (candidate) {
                case ASSISTED_CONCEPTION -> 0; // exact: it always wins
                case ULTRASOUND_CRL_FIRST_TRIMESTER -> FIRST_TRIMESTER_TOLERANCE_DAYS;
                case ULTRASOUND_EARLY_SECOND_TRIMESTER -> EARLY_SECOND_TRIMESTER_TOLERANCE_DAYS;
                case ULTRASOUND_LATE_SECOND_OR_THIRD -> null;
                // A remembered date does not overturn a measured one, at any discrepancy.
                case LMP_CERTAIN, LMP_UNCERTAIN, SYMPHYSIS_FUNDAL_HEIGHT, CLINICAL_ESTIMATE -> null;
                case UNKNOWN -> null;
            };
        }

        /** The gestation past which an ultrasound stops being a dating instrument. */
        public static int lateScanGestationDays() {
            return LATE_SCAN_GESTATION_DAYS;
        }

        @Override
        public String contentVersion() {
            return "obstetric-redating-engineering-seed-1.0.0";
        }

        @Override
        public String approvalStatus() {
            return "PENDING_MOHCC_RATIFICATION";
        }
    }
}
