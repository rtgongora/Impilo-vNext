package zw.gov.mohcc.impilo.clinical.rules.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Patient and encounter context supplied by calling systems (never inferred from free text alone).
 */
public record ClinicalEvaluationContext(
        Double ageYears,
        Integer ageDays,
        Double weightKg,
        String facilityLevel,
        List<String> diagnoses,
        List<MedicationLine> activeMedications,
        Integer empiricBroadSpectrumDays,
        Boolean cultureDocumented,
        Boolean lastResortAntibioticWithoutAstJustification
) {

    private static final List<String> BROAD = List.of(
            "meropenem", "imipenem", "piperacillin", "tazobactam", "cefepime", "ceftazidime"
    );

    @SuppressWarnings("unchecked")
    public static ClinicalEvaluationContext fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return new ClinicalEvaluationContext(null, null, null, null, List.of(), List.of(), null, null, null);
        }
        Double ageYears = toDouble(map.get("ageYears"));
        Integer ageDays = toInt(map.get("ageDays"));
        Double weightKg = toDouble(map.get("weightKg"));
        String facilityLevel = map.get("facilityLevel") != null ? map.get("facilityLevel").toString() : null;
        List<String> dx = new ArrayList<>();
        if (map.get("diagnoses") instanceof List<?> l) {
            for (Object o : l) {
                if (o != null) {
                    dx.add(o.toString().toUpperCase(Locale.ROOT));
                }
            }
        }
        List<MedicationLine> meds = new ArrayList<>();
        if (map.get("activeMedications") instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    String gen = str(m.get("genericName")).toLowerCase(Locale.ROOT);
                    meds.add(new MedicationLine(
                            gen,
                            str(m.get("displayName")),
                            toDouble(m.get("doseMg")),
                            str(m.get("route")),
                            toInt(m.get("daysOnTherapy")),
                            isBroadSpectrum(gen)
                    ));
                }
            }
        }
        Integer empDays = toInt(map.get("empiricBroadSpectrumDays"));
        Boolean culture = map.get("cultureDocumented") instanceof Boolean b ? b : null;
        Boolean lastResort = map.get("lastResortAntibioticWithoutAstJustification") instanceof Boolean b ? b : null;
        return new ClinicalEvaluationContext(ageYears, ageDays, weightKg, facilityLevel, dx, meds, empDays, culture, lastResort);
    }

    private static boolean isBroadSpectrum(String generic) {
        if (generic == null || generic.isBlank()) {
            return false;
        }
        return BROAD.stream().anyMatch(generic::contains);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static Double toDouble(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
