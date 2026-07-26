package zw.gov.mohcc.impilo.reproductive.history;

/**
 * Where the line falls between a late miscarriage and a stillbirth.
 *
 * <p>A national-policy value, not a constant. The threshold decides whether a loss is civilly
 * notifiable, whether it counts toward parity, and whether it enters perinatal mortality statistics
 * — so it belongs in governed content that a ministry can change, and every decision made against it
 * records which version applied.
 */
public interface LossThresholdPolicy {

    /** Gestation at or beyond which a loss is a stillbirth. */
    int stillbirthThresholdDays();

    /** Gestation separating early from late miscarriage. */
    int earlyLateMiscarriageBoundaryDays();

    /** Used only where gestation is unknown but a birth weight was recorded. */
    Integer minimumBirthWeightGrams();

    String contentVersion();

    String approvalStatus();

    static LossThresholdPolicy engineeringSeed() {
        return new EngineeringSeed();
    }

    /**
     * WHO's international-comparison threshold of 28+0 with a 1000 g weight fallback, and 12+0 for
     * early versus late miscarriage.
     *
     * <p>Flagged rather than assumed: many jurisdictions register stillbirths from 22 weeks or
     * 500 g, and Zimbabwe's own registration threshold must be confirmed before this is ratified.
     * Getting it wrong does not produce an error — it produces a loss that is quietly the wrong
     * category, which changes whether a family receives a birth certificate.
     */
    final class EngineeringSeed implements LossThresholdPolicy {

        @Override
        public int stillbirthThresholdDays() {
            return 28 * 7;
        }

        @Override
        public int earlyLateMiscarriageBoundaryDays() {
            return 12 * 7;
        }

        @Override
        public Integer minimumBirthWeightGrams() {
            return 1000;
        }

        @Override
        public String contentVersion() {
            return "loss-threshold-engineering-seed-1.0.0";
        }

        @Override
        public String approvalStatus() {
            return "PENDING_MOHCC_RATIFICATION";
        }
    }
}
