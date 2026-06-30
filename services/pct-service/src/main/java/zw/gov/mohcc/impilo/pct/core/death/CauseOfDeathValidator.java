package zw.gov.mohcc.impilo.pct.core.death;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces SOFT, non-blocking quality warnings on a WHO cause-of-death sequence (ill-defined /
 * "garbage" causes). Warnings never block signing — they prompt the certifier — consistent with WHO
 * ICD mortality-coding guidance. Hard blocks (eligibility, medico-legal) live elsewhere.
 */
@Component
public class CauseOfDeathValidator {

    private static final List<String> GARBAGE_IMMEDIATE = List.of(
            "cardiac arrest", "cardiopulmonary arrest", "respiratory arrest",
            "asystole", "old age", "natural causes", "organ failure", "shock");

    public List<String> warnings(String immediate, String antecedent, String underlying,
                                 String contributory) {
        List<String> w = new ArrayList<>();
        String imm = norm(immediate);

        if (imm.isBlank()) {
            w.add("Immediate cause (Part I line a) is required.");
        }
        for (String g : GARBAGE_IMMEDIATE) {
            if (imm.contains(g)) {
                w.add("\"" + immediate.trim() + "\" is a mode of dying, not a cause — record the "
                        + "disease or injury that led to it as the underlying cause.");
                break;
            }
        }
        if (imm.contains("respiratory failure") && norm(underlying).isBlank()) {
            w.add("\"Respiratory failure\" needs an underlying cause (Part I line c) — what caused it?");
        }
        if (norm(underlying).isBlank() && !imm.isBlank()) {
            w.add("No underlying cause recorded — the underlying cause is the single condition for "
                    + "national mortality statistics.");
        }
        if (!norm(antecedent).isBlank() && norm(underlying).isBlank()) {
            w.add("An antecedent cause is given without an underlying cause — complete the sequence.");
        }
        return w;
    }

    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(); }
}
