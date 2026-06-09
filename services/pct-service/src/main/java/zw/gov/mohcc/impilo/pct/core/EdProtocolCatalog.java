package zw.gov.mohcc.impilo.pct.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Maps ED presentations to clinical-knowledge-platform pathway references and protocol codes.
 * Pathway UUIDs align with CKP migration V005__ed_emergency_pathways.sql.
 */
public final class EdProtocolCatalog {

    record ProtocolSuggestion(String protocolCode, String pathwayRef, String label, String rationale, boolean recommended, int priority) {}

    private static final List<ProtocolSuggestion> CATALOG = List.of(
            new ProtocolSuggestion("SEPSIS", "f0000001-0001-4001-8001-000000000101", "Sepsis bundle", "Fever, shock, or infection with deterioration", false, 1),
            new ProtocolSuggestion("ANAPHYLAXIS", "f0000001-0001-4001-8001-000000000102", "Anaphylaxis", "Allergic reaction with airway or circulatory compromise", false, 2),
            new ProtocolSuggestion("ACS", "f0000001-0001-4001-8001-000000000103", "Acute coronary syndrome", "Chest pain with cardiac risk features", false, 3),
            new ProtocolSuggestion("STROKE", "f0000001-0001-4001-8001-000000000104", "Acute stroke", "Focal neurology within treatment window", false, 4),
            new ProtocolSuggestion("ASTHMA", "f0000001-0001-4001-8001-000000000001", "Acute asthma", "Wheeze, respiratory distress, SpO₂ fall", false, 5),
            new ProtocolSuggestion("DKA", "f0000001-0001-4001-8001-000000000105", "Diabetic ketoacidosis", "Hyperglycaemia, ketosis, acidosis features", false, 6),
            new ProtocolSuggestion("TRAUMA", "f0000001-0001-4001-8001-000000000106", "Major trauma", "Mechanism or physiology indicating trauma team", false, 7),
            new ProtocolSuggestion("POISONING", "f0000001-0001-4001-8001-000000000107", "Poisoning / overdose", "Toxic ingestion or overdose presentation", false, 8),
            new ProtocolSuggestion("ECTOPIC", "f0000001-0001-4001-8001-000000000108", "Ectopic pregnancy", "Abdominal pain in woman of childbearing age", false, 9),
            new ProtocolSuggestion("STATUS_EPILEPTICUS", "f0000001-0001-4001-8001-000000000109", "Status epilepticus", "Prolonged or recurrent seizures", false, 10),
            new ProtocolSuggestion("OBSTETRIC_EMERGENCY", "f0000001-0001-4001-8001-000000000110", "Obstetric emergency", "Pregnancy-related acute presentation", false, 11),
            new ProtocolSuggestion("RESPIRATORY_FAILURE", "f0000001-0001-4001-8001-000000000111", "Acute respiratory failure", "Severe hypoxia or hypercapnia", false, 12)
    );

    private EdProtocolCatalog() {}

    public static List<Map<String, Object>> suggest(String chiefComplaint, Integer acuity, String mechanism, boolean traumaActive) {
        String text = (Objects.requireNonNullElse(chiefComplaint, "") + " " + Objects.requireNonNullElse(mechanism, ""))
                .toLowerCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProtocolSuggestion s : CATALOG) {
            boolean recommended = matches(text, acuity, traumaActive, s.protocolCode());
            out.add(Map.of(
                    "protocol_code", s.protocolCode(),
                    "pathway_ref", s.pathwayRef(),
                    "label", s.label(),
                    "rationale", s.rationale(),
                    "recommended", recommended,
                    "priority", s.priority()));
        }
        out.sort((a, b) -> Boolean.compare((Boolean) b.get("recommended"), (Boolean) a.get("recommended")));
        return out;
    }

    private static boolean matches(String text, Integer acuity, boolean traumaActive, String code) {
        if (traumaActive && "TRAUMA".equals(code)) return true;
        return switch (code) {
            case "SEPSIS" -> containsAny(text, "sepsis", "septic", "fever", "rigor", "shock", "infection");
            case "ANAPHYLAXIS" -> containsAny(text, "anaphyl", "allerg", "urticaria", "angioedema", "epipen");
            case "ACS" -> containsAny(text, "chest pain", "cardiac", "mi", "angina", "stemi");
            case "STROKE" -> containsAny(text, "stroke", "facial droop", "hemipleg", "aphasia", "cva");
            case "ASTHMA" -> containsAny(text, "asthma", "wheeze", "bronchospasm", "salbutamol");
            case "DKA" -> containsAny(text, "dka", "ketoacid", "hyperglyc", "diabetic coma");
            case "TRAUMA" -> containsAny(text, "trauma", "rta", "fall", "stab", "gunshot", "assault", "burn");
            case "POISONING" -> containsAny(text, "overdose", "poison", "ingestion", "toxic", "paracetamol");
            case "ECTOPIC" -> containsAny(text, "ectopic", "pregnant", "pv bleed", "abdominal pain");
            case "STATUS_EPILEPTICUS" -> containsAny(text, "seizure", "convulsion", "epilep", "fitting");
            case "OBSTETRIC_EMERGENCY" -> containsAny(text, "labour", "obstetric", "antepartum", "postpartum", "eclamps");
            case "RESPIRATORY_FAILURE" -> containsAny(text, "respiratory failure", "hypoxia", "spo2", "dyspnoea", "gasping")
                    || (acuity != null && acuity <= 2 && containsAny(text, "breath", "respir"));
            default -> false;
        };
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) return true;
        }
        return false;
    }

    public static String zoneForAcuity(int acuity, boolean fastTrack) {
        if (fastTrack) return "FAST_TRACK";
        return switch (acuity) {
            case 1 -> "RESUS";
            case 2 -> "RESUS";
            case 3 -> "MAJORS";
            case 4 -> "MINORS";
            default -> "MINORS";
        };
    }
}
