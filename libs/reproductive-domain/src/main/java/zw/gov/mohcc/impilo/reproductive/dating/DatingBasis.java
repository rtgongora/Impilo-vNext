package zw.gov.mohcc.impilo.reproductive.dating;

import java.time.LocalDate;

/**
 * One candidate way of dating a pregnancy, with the observation it rests on.
 *
 * <p>Only the fields relevant to {@link #method()} are populated; the record is deliberately wide
 * rather than a hierarchy, because these are rows in a clinical register that a person reads, not a
 * class model. What matters is that a rejected basis can be stored beside the adopted one with
 * enough detail to explain the decision later.
 */
public record DatingBasis(
        DatingMethod method,
        LocalDate observedOn,
        LocalDate lastMenstrualPeriod,
        Boolean lastMenstrualPeriodCertain,
        Integer measuredGestationalAgeDays,
        LocalDate conceptionDate,
        Integer embryoAgeDaysAtTransfer,
        String sourceRef) {

    public static DatingBasis fromLmp(LocalDate lmp, boolean certain) {
        return new DatingBasis(
                certain ? DatingMethod.LMP_CERTAIN : DatingMethod.LMP_UNCERTAIN,
                lmp, lmp, certain, null, null, null, null);
    }

    /**
     * An ultrasound. The method is derived from the gestation at the time of the scan rather than
     * supplied, because that is what determines how much the scan should be trusted, and leaving it
     * to the caller invites a late scan being recorded as a first-trimester one.
     */
    public static DatingBasis fromUltrasound(LocalDate scannedOn, Integer gestationalAgeDays, String reportRef) {
        if (gestationalAgeDays == null) {
            return new DatingBasis(DatingMethod.UNKNOWN, scannedOn, null, null,
                    null, null, null, reportRef);
        }
        DatingMethod method;
        if (gestationalAgeDays < 14 * 7) {
            method = DatingMethod.ULTRASOUND_CRL_FIRST_TRIMESTER;
        } else if (gestationalAgeDays < 22 * 7) {
            method = DatingMethod.ULTRASOUND_EARLY_SECOND_TRIMESTER;
        } else {
            method = DatingMethod.ULTRASOUND_LATE_SECOND_OR_THIRD;
        }
        return new DatingBasis(method, scannedOn, null, null, gestationalAgeDays, null, null, reportRef);
    }

    public static DatingBasis fromAssistedConception(LocalDate transferDate, Integer embryoAgeDaysAtTransfer,
                                                     String cycleRef) {
        return new DatingBasis(DatingMethod.ASSISTED_CONCEPTION, transferDate, null, null,
                null, transferDate, embryoAgeDaysAtTransfer, cycleRef);
    }

    public static DatingBasis clinicalEstimate(LocalDate assessedOn, Integer gestationalAgeDays, String note) {
        return new DatingBasis(DatingMethod.CLINICAL_ESTIMATE, assessedOn, null, null,
                gestationalAgeDays, null, null, note);
    }

    /** The estimated delivery date this basis implies, or null when it cannot produce one. */
    public LocalDate estimatedDeliveryDate() {
        if (method == null || !method.usable()) {
            return null;
        }
        return switch (method) {
            case ASSISTED_CONCEPTION ->
                    EddCalculator.fromAssistedConception(conceptionDate, embryoAgeDaysAtTransfer);
            case LMP_CERTAIN, LMP_UNCERTAIN ->
                    EddCalculator.fromLastMenstrualPeriod(lastMenstrualPeriod);
            default ->
                    EddCalculator.fromMeasuredGestationalAge(observedOn, measuredGestationalAgeDays);
        };
    }

    /** True when this basis can date the pregnancy at all. */
    public boolean usable() {
        return estimatedDeliveryDate() != null;
    }
}
