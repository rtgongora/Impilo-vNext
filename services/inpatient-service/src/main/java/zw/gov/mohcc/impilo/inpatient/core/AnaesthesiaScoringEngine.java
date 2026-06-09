package zw.gov.mohcc.impilo.inpatient.core;

import java.util.*;

/**
 * Clinical anaesthetic scoring calculators and risk-band interpretation.
 * Health workers choose which instrument to apply; this engine computes totals and suggests tools.
 */
public final class AnaesthesiaScoringEngine {

    private AnaesthesiaScoringEngine() {}

    public static final List<String> SCORE_TYPES = List.of(
            "ASA", "MALLAMPATI", "CORMACK_LEHANE", "STOP_BANG", "APFEL", "RCRI", "INTRAOP_VITALS", "ALDRETE", "PAIN");

    public record ScoreResult(
            String scoreType,
            String phase,
            Map<String, Object> components,
            Integer totalScore,
            String interpretation,
            String riskBand) {}

    public record ScoreSuggestion(
            String scoreType,
            String reason,
            boolean recommended,
            int priority) {}

    public static List<ScoreSuggestion> suggestScores(String procedureName, String procedureCode, String asaClass) {
        String context = (procedureName + " " + Objects.requireNonNullElse(procedureCode, "")).toLowerCase(Locale.ROOT);
        List<ScoreSuggestion> out = new ArrayList<>();
        out.add(new ScoreSuggestion("ASA", "Required baseline anaesthetic risk classification", true, 1));
        out.add(new ScoreSuggestion("MALLAMPATI", "Airway assessment before general anaesthesia", true, 2));

        if (context.contains("cardiac") || context.contains("cabg") || context.contains("vascular")
                || isAsaAtLeast(asaClass, "III")) {
            out.add(new ScoreSuggestion("RCRI", "Cardiac/vascular procedure or ASA ≥ III — cardiac risk index indicated", true, 3));
        }
        if (context.contains("laparoscop") || context.contains("general") || context.contains("abdom")
                || context.contains("gyna") || context.contains("append")) {
            out.add(new ScoreSuggestion("APFEL", "General anaesthesia — PONV prophylaxis scoring suggested", true, 4));
        }
        if (context.contains("bariatric") || context.contains("sleep") || context.contains("osa")
                || isAsaAtLeast(asaClass, "III")) {
            out.add(new ScoreSuggestion("STOP_BANG", "Obesity/OSA risk context — screening for obstructive sleep apnoea", true, 5));
        }
        if (context.contains("intub") || context.contains("difficult airway")) {
            out.add(new ScoreSuggestion("CORMACK_LEHANE", "Anticipated difficult airway — document view grade", true, 6));
        }
        out.add(new ScoreSuggestion("INTRAOP_VITALS", "Record structured intra-operative vitals snapshots", false, 7));
        out.add(new ScoreSuggestion("ALDRETE", "PACU recovery readiness (post-operative)", false, 8));
        out.add(new ScoreSuggestion("PAIN", "PACU pain assessment", false, 9));
        out.sort(Comparator.comparingInt(ScoreSuggestion::priority));
        return out;
    }

    public static ScoreResult calculate(String scoreType, String phase, Map<String, Object> components) {
        String normalized = scoreType.toUpperCase(Locale.ROOT).replace("-", "_");
        return switch (normalized) {
            case "ASA" -> scoreAsa(phase, components);
            case "MALLAMPATI" -> scoreMallampati(phase, components);
            case "CORMACK_LEHANE", "CORMACK" -> scoreCormackLehane(phase, components);
            case "STOP_BANG", "STOPBANG" -> scoreStopBang(phase, components);
            case "APFEL" -> scoreApfel(phase, components);
            case "RCRI" -> scoreRcri(phase, components);
            case "INTRAOP_VITALS" -> scoreIntraopVitals(phase, components);
            case "ALDRETE" -> scoreAldrete(phase, components);
            case "PAIN" -> scorePain(phase, components);
            default -> throw new IllegalArgumentException("Unsupported score type: " + scoreType);
        };
    }

    private static ScoreResult scoreAsa(String phase, Map<String, Object> c) {
        String asa = str(c, "asaClass", "asa_class", "class");
        if (asa == null || asa.isBlank()) {
            throw new IllegalArgumentException("asaClass is required for ASA scoring");
        }
        String normalized = asa.toUpperCase(Locale.ROOT).replace("ASA", "").trim();
        String band = switch (normalized) {
            case "I" -> "LOW";
            case "II" -> "LOW";
            case "III" -> "MODERATE";
            case "IV" -> "HIGH";
            case "V", "VI" -> "CRITICAL";
            default -> "MODERATE";
        };
        return new ScoreResult("ASA", phase, Map.of("asa_class", normalized), null,
                "ASA " + normalized + " — " + asaInterpretation(normalized), band);
    }

    private static String asaInterpretation(String asa) {
        return switch (asa) {
            case "I" -> "Normal healthy patient";
            case "II" -> "Mild systemic disease";
            case "III" -> "Severe systemic disease";
            case "IV" -> "Severe disease constant threat to life";
            case "V" -> "Moribund, not expected to survive without operation";
            case "VI" -> "Brain-dead organ donor";
            default -> "Document perioperative risk with anaesthetic plan";
        };
    }

    private static ScoreResult scoreMallampati(String phase, Map<String, Object> c) {
        String mp = str(c, "mallampatiClass", "mallampati_class", "class");
        if (mp == null) throw new IllegalArgumentException("mallampatiClass is required");
        String roman = mp.toUpperCase(Locale.ROOT).replace("CLASS", "").trim();
        String band = switch (roman) {
            case "I", "II" -> "LOW";
            case "III" -> "MODERATE";
            case "IV" -> "HIGH";
            default -> "MODERATE";
        };
        return new ScoreResult("MALLAMPATI", phase, Map.of("mallampati_class", roman), null,
                "Mallampati " + roman + " — " + mallampatiInterpretation(roman), band);
    }

    private static String mallampatiInterpretation(String mp) {
        return switch (mp) {
            case "I" -> "Full visibility of soft palate, fauces, uvula, pillars";
            case "II" -> "Soft palate, fauces, portion of uvula visible";
            case "III" -> "Soft palate, base of uvula visible — anticipate more difficult mask ventilation";
            case "IV" -> "Hard palate only — high risk difficult intubation; plan awake/alternative airway";
            default -> "Document airway plan";
        };
    }

    private static ScoreResult scoreCormackLehane(String phase, Map<String, Object> c) {
        String grade = str(c, "cormackLehaneGrade", "cormack_lehane_grade", "grade");
        if (grade == null) throw new IllegalArgumentException("cormackLehaneGrade is required");
        String g = grade.toUpperCase(Locale.ROOT).replace("GRADE", "").trim();
        String band = switch (g) {
            case "I", "II" -> "LOW";
            case "III" -> "MODERATE";
            case "IV" -> "HIGH";
            default -> "MODERATE";
        };
        return new ScoreResult("CORMACK_LEHANE", phase, Map.of("cormack_lehane_grade", g), null,
                "Cormack-Lehane " + g + " — " + cormackInterpretation(g), band);
    }

    private static String cormackInterpretation(String g) {
        return switch (g) {
            case "I" -> "Full glottic view";
            case "II" -> "Partial glottic view";
            case "III" -> "Epiglottis only — difficult intubation";
            case "IV" -> "No glottic structures — failed conventional laryngoscopy";
            default -> "Document airway rescue plan";
        };
    }

    private static ScoreResult scoreStopBang(String phase, Map<String, Object> c) {
        int total = 0;
        Map<String, Object> comp = new LinkedHashMap<>();
        if (bool(c, "snoring")) { total++; comp.put("snoring", true); }
        if (bool(c, "tired")) { total++; comp.put("tired", true); }
        if (bool(c, "observedApnea", "observed_apnea")) { total++; comp.put("observed_apnea", true); }
        if (bool(c, "pressure", "hypertension")) { total++; comp.put("pressure", true); }
        if (bool(c, "bmiGreaterThan35", "bmi_greater_than_35")) { total++; comp.put("bmi_greater_than_35", true); }
        if (bool(c, "ageGreaterThan50", "age_greater_than_50")) { total++; comp.put("age_greater_than_50", true); }
        if (bool(c, "neckCircumference", "neck_circumference")) { total++; comp.put("neck_circumference", true); }
        if (bool(c, "genderMale", "gender_male")) { total++; comp.put("gender_male", true); }
        String band = total >= 5 ? "HIGH" : total >= 3 ? "MODERATE" : "LOW";
        String interp = total >= 5
                ? "High risk OSA — consider sleep study, difficult airway plan, post-op monitoring"
                : total >= 3
                ? "Intermediate OSA risk — enhanced perioperative vigilance"
                : "Low OSA screening score";
        return new ScoreResult("STOP_BANG", phase, comp, total, "STOP-BANG " + total + "/8 — " + interp, band);
    }

    private static ScoreResult scoreApfel(String phase, Map<String, Object> c) {
        int total = 0;
        Map<String, Object> comp = new LinkedHashMap<>();
        if (bool(c, "female")) { total++; comp.put("female", true); }
        if (bool(c, "nonSmoker", "non_smoker")) { total++; comp.put("non_smoker", true); }
        if (bool(c, "historyPonv", "history_ponv", "motionSickness")) { total++; comp.put("history_ponv", true); }
        if (bool(c, "postopOpioids", "postop_opioids")) { total++; comp.put("postop_opioids", true); }
        String band = total >= 3 ? "HIGH" : total >= 2 ? "MODERATE" : "LOW";
        String interp = total >= 3
                ? "High PONV risk (~60%) — multimodal prophylaxis recommended"
                : total == 2
                ? "Moderate PONV risk (~40%)"
                : total == 1
                ? "Low-moderate PONV risk (~20%)"
                : "Low PONV risk (~10%)";
        return new ScoreResult("APFEL", phase, comp, total, "Apfel " + total + "/4 — " + interp, band);
    }

    private static ScoreResult scoreRcri(String phase, Map<String, Object> c) {
        int total = 0;
        Map<String, Object> comp = new LinkedHashMap<>();
        if (bool(c, "highRiskSurgery", "high_risk_surgery")) { total++; comp.put("high_risk_surgery", true); }
        if (bool(c, "ischemicHeartDisease", "ischemic_heart_disease")) { total++; comp.put("ischemic_heart_disease", true); }
        if (bool(c, "congestiveHeartFailure", "congestive_heart_failure", "chf")) { total++; comp.put("chf", true); }
        if (bool(c, "cerebrovascularDisease", "cerebrovascular_disease")) { total++; comp.put("cerebrovascular_disease", true); }
        if (bool(c, "diabetesInsulin", "diabetes_insulin")) { total++; comp.put("diabetes_insulin", true); }
        if (bool(c, "renalInsufficiency", "creatinine_gt_2", "creatinineGreaterThan2")) { total++; comp.put("renal_insufficiency", true); }
        String band = total >= 3 ? "HIGH" : total >= 1 ? "MODERATE" : "LOW";
        String interp = switch (total) {
            case 0 -> "RCRI 0 — major cardiac event risk ~0.4%";
            case 1 -> "RCRI 1 — risk ~0.9%";
            case 2 -> "RCRI 2 — risk ~6.6%";
            default -> "RCRI " + total + "+ — risk ≥11% — cardiology/ICU planning advised";
        };
        return new ScoreResult("RCRI", phase, comp, total, interp, band);
    }

    private static ScoreResult scoreIntraopVitals(String phase, Map<String, Object> c) {
        Integer hr = integer(c, "heartRate", "heart_rate", "hr");
        Integer sbp = integer(c, "systolicBp", "systolic_bp", "sbp");
        Integer dbp = integer(c, "diastolicBp", "diastolic_bp", "dbp");
        Integer spo2 = integer(c, "spo2", "oxygen_saturation");
        Integer etco2 = integer(c, "etco2", "end_tidal_co2");
        Map<String, Object> comp = new LinkedHashMap<>();
        if (hr != null) comp.put("heart_rate", hr);
        if (sbp != null) comp.put("systolic_bp", sbp);
        if (dbp != null) comp.put("diastolic_bp", dbp);
        if (spo2 != null) comp.put("spo2", spo2);
        if (etco2 != null) comp.put("etco2", etco2);
        int alerts = 0;
        if (hr != null && (hr < 50 || hr > 100)) alerts++;
        if (sbp != null && (sbp < 90 || sbp > 160)) alerts++;
        if (spo2 != null && spo2 < 94) alerts++;
        if (etco2 != null && (etco2 < 35 || etco2 > 45)) alerts++;
        String band = alerts >= 2 ? "HIGH" : alerts == 1 ? "MODERATE" : "LOW";
        return new ScoreResult("INTRAOP_VITALS", phase, comp, alerts,
                alerts > 0 ? alerts + " vital parameter(s) outside typical anaesthetic targets" : "Vitals within typical targets",
                band);
    }

    private static ScoreResult scoreAldrete(String phase, Map<String, Object> c) {
        Integer total = integer(c, "aldreteScore", "aldrete_score", "totalScore", "total");
        if (total == null) throw new IllegalArgumentException("aldreteScore is required");
        String band = total >= 9 ? "LOW" : total >= 7 ? "MODERATE" : "HIGH";
        String interp = total >= 9
                ? "Ready for PACU discharge to ward"
                : total >= 7
                ? "Continue PACU monitoring"
                : "Remain in PACU — recovery incomplete";
        return new ScoreResult("ALDRETE", phase, Map.of("aldrete_score", total), total, interp, band);
    }

    private static ScoreResult scorePain(String phase, Map<String, Object> c) {
        Integer total = integer(c, "painScore", "pain_score", "score");
        if (total == null) throw new IllegalArgumentException("painScore is required");
        String band = total <= 3 ? "LOW" : total <= 6 ? "MODERATE" : "HIGH";
        return new ScoreResult("PAIN", phase, Map.of("pain_score", total), total,
                "Pain " + total + "/10 — " + (total > 6 ? "analgesia escalation indicated" : "acceptable with plan"), band);
    }

    private static boolean isAsaAtLeast(String asa, String min) {
        if (asa == null) return false;
        String n = asa.toUpperCase(Locale.ROOT).replace("ASA", "").trim();
        try {
            return Integer.parseInt(romanOrDigit(n)) >= Integer.parseInt(romanOrDigit(min));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String romanOrDigit(String v) {
        return switch (v) {
            case "I" -> "1";
            case "II" -> "2";
            case "III" -> "3";
            case "IV" -> "4";
            case "V" -> "5";
            case "VI" -> "6";
            default -> v;
        };
    }

    private static boolean bool(Map<String, Object> c, String... keys) {
        for (String k : keys) {
            Object v = c.get(k);
            if (v instanceof Boolean b) return b;
            if (v != null && Boolean.parseBoolean(String.valueOf(v))) return true;
        }
        return false;
    }

    private static Integer integer(Map<String, Object> c, String... keys) {
        for (String k : keys) {
            Object v = c.get(k);
            if (v instanceof Number n) return n.intValue();
            if (v != null) {
                try {
                    return Integer.parseInt(String.valueOf(v));
                } catch (NumberFormatException ignored) { /* next key */ }
            }
        }
        return null;
    }

    private static String str(Map<String, Object> c, String... keys) {
        for (String k : keys) {
            Object v = c.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v).trim();
        }
        return null;
    }
}
