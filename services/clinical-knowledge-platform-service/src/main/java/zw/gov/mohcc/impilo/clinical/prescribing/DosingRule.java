package zw.gov.mohcc.impilo.clinical.prescribing;

import java.util.List;

/**
 * A weight-based dosing rule as governed content.
 *
 * @param mgPerKgPerDose  milligrams per kilogram per dose; null for a fixed dose
 * @param fixedDoseMg     a dose that does not scale with weight, e.g. zinc in diarrhoea
 * @param roundToMg       the increment a dose is rounded to, so the answer is measurable
 * @param specialistOnly  the medicine should only be started under specialist direction
 */
public record DosingRule(
        String code,
        String genericName,
        String atcCode,
        String indication,
        String route,
        Integer ageMinDays,
        Integer ageMaxDays,
        Double weightMinKg,
        Double weightMaxKg,
        Double mgPerKgPerDose,
        Double fixedDoseMg,
        Integer dosesPerDay,
        Double maxSingleDoseMg,
        Double maxDailyDoseMg,
        Integer durationDays,
        Double roundToMg,
        boolean specialistOnly,
        boolean renalCaution,
        List<Concentration> concentrations,
        String monitoring,
        List<String> sourceRefs,
        String approvalStatus,
        String adaptationAuthority,
        String contentVersion,
        List<TestCase> testCases) {

    /**
     * True when this rule covers the patient. Age and weight are both required: a dose
     * calculated without a verified weight is the single most common paediatric medication
     * error, and an age-inappropriate rule can be off by an order of magnitude.
     */
    public boolean matches(String medicine, String indicationCode, Integer ageDays, Double weightKg) {
        if (medicine == null || !medicine.equalsIgnoreCase(genericName)) {
            return false;
        }
        if (indication != null && indicationCode != null && !indication.equalsIgnoreCase(indicationCode)) {
            return false;
        }
        if (ageDays == null || weightKg == null) {
            return false;
        }
        if (ageMinDays != null && ageDays < ageMinDays) {
            return false;
        }
        if (ageMaxDays != null && ageDays > ageMaxDays) {
            return false;
        }
        if (weightMinKg != null && weightKg < weightMinKg) {
            return false;
        }
        return weightMaxKg == null || weightKg <= weightMaxKg;
    }

    public boolean ratified() {
        return "APPROVED".equalsIgnoreCase(approvalStatus);
    }

    /** @param mgPerMl concentration of a liquid preparation; zero for a solid form */
    public record Concentration(String label, double mgPerMl) {
        public boolean liquid() {
            return mgPerMl > 0;
        }
    }

    /** A fixture that travels with the rule so a content edit cannot silently change a dose. */
    public record TestCase(
            String name,
            Integer ageDays,
            Double weightKg,
            Double expectMgPerDose,
            boolean expectCapped) {
    }
}
