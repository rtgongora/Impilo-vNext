package zw.gov.mohcc.impilo.clinical.prescribing;

import java.util.Locale;
import java.util.Optional;

/**
 * Illustrative weight-band table for neonatal gentamicin (engineering seed — replace with
 * MoHCC-approved table after clinical sign-off and PDF extraction).
 */
public final class NeonatalGentamicinDosing {

    private NeonatalGentamicinDosing() {
    }

    /**
     * @param weightKg birth / current weight depending on local protocol
     * @param ageDays   postnatal age in days
     */
    public static Optional<String> suggestMgPerDose(double weightKg, int ageDays) {
        if (ageDays > 28 || weightKg <= 0 || weightKg > 6) {
            return Optional.empty();
        }
        double mg;
        if (weightKg < 1.2) {
            mg = 3.0;
        } else if (weightKg < 2.0) {
            mg = 4.0;
        } else if (weightKg < 2.5) {
            mg = 5.0;
        } else {
            mg = 5.5;
        }
        return Optional.of(String.format(Locale.ROOT, "%.1f mg IV per dose (seed table; verify neonatal unit chart)", mg));
    }
}
