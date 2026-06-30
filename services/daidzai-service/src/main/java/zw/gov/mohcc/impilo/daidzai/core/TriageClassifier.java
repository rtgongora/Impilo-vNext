package zw.gov.mohcc.impilo.daidzai.core;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Rules-based emergency triage. Maps an emergency category + reported severity to a triage
 * category (RED/ORANGE/YELLOW/GREEN — and BLACK only for explicit deceased reports). This is a
 * deterministic, auditable first-pass classification — it never overrides a clinician's later
 * judgement (PCT owns the clinical encounter). Detailed protocol-driven scoring (MCI tags,
 * outbreak case-definitions) is an owner-routed/depth-deferred seam — see the ledger.
 */
@Component
public class TriageClassifier {

    private static final Set<String> TIME_CRITICAL = Set.of(
            "CARDIAC", "CARDIAC_ARREST", "RESPIRATORY", "AIRWAY", "STROKE", "OBSTETRIC",
            "MAJOR_TRAUMA", "HAEMORRHAGE", "ANAPHYLAXIS", "POISONING");

    /** @return one of RED/ORANGE/YELLOW/GREEN/BLACK */
    public String classify(String emergencyCategory, String reportedSeverity) {
        String cat = emergencyCategory == null ? "" : emergencyCategory.trim().toUpperCase();
        String sev = reportedSeverity == null ? "UNKNOWN" : reportedSeverity.trim().toUpperCase();

        if ("DECEASED".equals(cat) || "FATALITY".equals(cat)) {
            return "BLACK";
        }
        if ("CRITICAL".equals(sev) || TIME_CRITICAL.contains(cat)) {
            return "RED";
        }
        if ("HIGH".equals(sev)) {
            return "ORANGE";
        }
        if ("MODERATE".equals(sev)) {
            return "YELLOW";
        }
        if ("LOW".equals(sev)) {
            return "GREEN";
        }
        // UNKNOWN severity with a non-time-critical category: precautionary YELLOW.
        return "YELLOW";
    }

    /** Maps a triage category to the incident severity stored on the aggregate. */
    public String severityForTriage(String triageCategory) {
        return switch (triageCategory) {
            case "RED", "BLACK" -> "CRITICAL";
            case "ORANGE" -> "HIGH";
            case "YELLOW" -> "MODERATE";
            case "GREEN" -> "LOW";
            default -> "UNKNOWN";
        };
    }

    /**
     * Categories whose subject identity + detail must be protected unless policy grants access
     * (violence / sexual-assault / mental-health / child-protection). Drives the {@code sensitive}
     * flag, which the PDP consumes to mask the record from non-authorised actors.
     */
    public boolean isSensitiveCategory(String emergencyCategory) {
        if (emergencyCategory == null) return false;
        String c = emergencyCategory.trim().toUpperCase();
        return c.equals("VIOLENCE") || c.equals("GBV") || c.equals("SEXUAL_ASSAULT")
                || c.equals("MENTAL_HEALTH") || c.equals("SELF_HARM") || c.equals("SUICIDE")
                || c.equals("CHILD_PROTECTION") || c.equals("SAFEGUARDING");
    }
}
