package zw.gov.mohcc.impilo.clinical.assistant;

import java.util.Locale;
import java.util.Set;

public final class QuestionClassifier {

    private QuestionClassifier() {
    }

    public static String classify(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (matches(q, "dose", "mg", "how much", "ceftriaxone", "gentamicin")) {
            return "DOSING_QUERY";
        }
        if (matches(q, "what changed", "version", "2024", "2025")) {
            return "VERSION_DIFFERENCE";
        }
        if (matches(q, "pathway", "step", "assessment")) {
            return "PATHWAY_SUPPORT";
        }
        if (matches(q, "prescribe", "order", "therapy", "regimen")) {
            return "PRESCRIBING_SUPPORT";
        }
        if (matches(q, "why", "explain", "education")) {
            return "EDUCATIONAL_EXPLANATION";
        }
        return "GUIDELINE_LOOKUP";
    }

    private static boolean matches(String q, String... tokens) {
        for (String t : tokens) {
            if (q.contains(t)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> topicTokens(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        java.util.HashSet<String> s = new java.util.HashSet<>();
        if (q.contains("asthma") || q.contains("salbutamol") || q.contains("saba") || q.contains("inhaled corticosteroid")) {
            s.add("asthma");
        }
        if (q.contains("gonorrhoea") || q.contains("gonorrhea") || q.contains("sti")) {
            s.add("gonorrhoea");
        }
        if (q.contains("neonat") || q.contains("sepsis")) {
            s.add("neonatal_sepsis");
        }
        if (q.contains("uti") || q.contains("urinary")) {
            s.add("uti");
        }
        if (q.contains("diabetes") || q.contains("metformin")) {
            s.add("diabetes");
        }
        if (q.contains("heart failure") || q.contains("hf ") || q.contains("heart-failure")) {
            s.add("heart_failure");
        }
        if (q.contains("fever") || q.contains("pyrexia")) {
            s.add("fever");
        }
        if (q.contains("tb") || q.contains("isoniazid") || q.contains("tpt")) {
            s.add("tpt");
        }
        return s;
    }
}
