package zw.gov.mohcc.impilo.pct.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import zw.gov.mohcc.impilo.emergency.triage.IittChart;
import zw.gov.mohcc.impilo.emergency.triage.TriageFacts;
import zw.gov.mohcc.impilo.emergency.triage.TriagePriority;
import zw.gov.mohcc.impilo.emergency.triage.TriageResult;
import zw.gov.mohcc.impilo.emergency.triage.WhoIittEngine;

/**
 * Turns a triage request into a triage decision, with WHO IITT as the authority of record.
 *
 * <p>This is the enactment of the product-owner ruling that IITT is the sole triage authority
 * (R7). It is deliberately a pure function of the request — no Spring, no database, no clock — so
 * the safety-critical logic can be exercised exhaustively in a plain unit test rather than only
 * behind a running service. {@link EdVisitService} calls {@link #decide} and then persists; nothing
 * clinical is decided in the persistence layer.
 *
 * <p>Three things this replaces, each a verified live defect:
 * <ul>
 *   <li>{@code scoreBoth} returned {@code Math.min(esi, mts)} as the acuity and overwrote the
 *       recorded triage system with whichever scored lower — a silent reconciliation that could
 *       stamp a row {@code MTS} when the clinician chose {@code ESI}. ESI/MTS are now advisory only;
 *       they can never write the acuity and never overwrite the tool of record.</li>
 *   <li>{@code if (acuity == null) acuity = 3;} — an unassessed patient written down as the middle
 *       acuity. There is now no default acuity: an unassessed patient is a refusal
 *       ({@code NOT_TRIAGEABLE}), returned to the caller so the assessment is completed, never a
 *       reassuring 3.</li>
 *   <li>a cross-system disagreement was resolved silently by taking the lower number. A material
 *       disagreement now raises {@code requiresClinicianReview} — IITT's own "clinical concern"
 *       up-triage mechanism, a human with a record — rather than an automatic up-triage.</li>
 * </ul>
 */
public final class EmergencyTriageDecider {

    /**
     * The documented IITT-tier → acuity mapping. The three IITT tiers are the primary record
     * (kept first-class in {@code iitt_priority}); acuity 1-5 is a derived operational sort key so
     * every existing board and queue keeps working. RED is the most urgent (1), GREEN the least (5),
     * YELLOW the middle (3) — leaving 2 and 4 for the finer-grained advisory systems to occupy in
     * their own column without ever being the acuity of record.
     */
    static int acuityFor(TriagePriority priority) {
        return switch (priority) {
            case RED -> 1;
            case YELLOW -> 3;
            case GREEN -> 5;
            case NOT_TRIAGEABLE -> throw new IllegalArgumentException(
                    "NOT_TRIAGEABLE has no acuity — it is a refusal, not a triage");
        };
    }

    /**
     * How far the advisory system must be more urgent than the IITT decision before a human is
     * asked to reconcile. A one-step difference is expected noise between a three-tier tool and a
     * five-point scale (IITT YELLOW=3 vs an advisory ESI 2 is not a real disagreement). Two steps
     * more urgent is a material under-triage signal and must not be resolved silently.
     */
    private static final int DISCREPANCY_MARGIN = 2;

    private EmergencyTriageDecider() {
    }

    /**
     * @param body          the triage request
     * @param advisory      the {@code scoreBoth} advisory map (esi/mts/recommended_acuity), or null
     *                      when no discriminators were supplied — advisory only, never the acuity
     * @param declaredSystem the caller's declared triage system (for a manual acuity entry)
     * @param explicitAcuity a clinician-entered acuity, or null
     */
    public static TriageDecision decide(Map<String, Object> body,
                                        Map<String, Object> advisory,
                                        String declaredSystem,
                                        Integer explicitAcuity) {
        TriageFacts facts = toFacts(body);
        boolean hasIittInputs = !facts.assessedSigns().isEmpty() || !facts.measuredVitals().isEmpty();

        // IITT governs whenever it CAN run — that is what "sole authority" means operationally. It
        // can run only when there are IITT inputs and a resolvable age (age selects the chart and
        // there is no safe default chart). When it cannot run, a clinician-entered acuity stands as
        // a manual triage; only when there is neither a runnable IITT nor an explicit acuity is the
        // request refused — never a default acuity 3.
        TriageResult result = null;
        if (hasIittInputs) {
            try {
                result = WhoIittEngine.evaluate(facts);
            } catch (IllegalArgumentException ageMissing) {
                // IITT cannot run without age. If the clinician also gave an explicit acuity, honour
                // it as a manual entry below; otherwise this is an honest refusal, not a guess.
                if (explicitAcuity == null) {
                    return TriageDecision.refused("age_required", ageMissing.getMessage(), List.of());
                }
            }
        }

        if (result != null && result.priority() != TriagePriority.NOT_TRIAGEABLE) {
            int acuity = acuityFor(result.priority());
            boolean review = result.requiresClinicianReview();
            String reviewReason = review
                    ? "IITT step-3 high-risk vital signs — chart instruction is up-triage or immediate "
                            + "review by a supervising clinician: " + result.highRiskVitalSigns()
                    : null;

            // Cross-system discrepancy: raise it for a human, never silently take the lower number.
            if (advisory != null) {
                Integer advisoryAcuity = asInt(advisory.get("recommended_acuity"));
                if (advisoryAcuity != null && advisoryAcuity <= acuity - DISCREPANCY_MARGIN) {
                    review = true;
                    reviewReason = append(reviewReason,
                            "Advisory ESI/MTS acuity " + advisoryAcuity + " is materially more urgent than "
                                    + "the IITT decision " + result.priority() + " (acuity " + acuity
                                    + "); reconcile before proceeding — the advisory does not down- or up-triage on its own.");
                }
            }

            // A clinician-entered acuity alongside a runnable IITT is an override on an IITT category.
            // IITT stays the tier of record (never silently down- or up-triaged to the entered number);
            // the disagreement is surfaced for reconciliation. The full reason-mandatory, countersigned
            // override mechanism is a later wave — this is the safe interim: raise it, do not bury it.
            if (explicitAcuity != null && explicitAcuity != acuity) {
                review = true;
                reviewReason = append(reviewReason,
                        "Clinician entered acuity " + explicitAcuity + " but IITT assessed "
                                + result.priority() + " (acuity " + acuity + "); IITT is retained as the "
                                + "tier of record — reconcile or record a countersigned override.");
            }

            return new TriageDecision(true, acuity, "WHO_IITT", result.priority().name(),
                    result.chart(), result.triggeringFindings(), result.highRiskVitalSigns(),
                    result.unassessedInputs(), review, reviewReason, signMap(body), advisory, null, null);
        }

        // IITT ran but could not triage (NOT_TRIAGEABLE), or did not run. A clinician-entered acuity
        // is a manual decision and stands — flagged when it overrides an incomplete IITT assessment,
        // so the incompleteness is on the record and never masquerades as a complete triage.
        if (explicitAcuity != null) {
            boolean overIncompleteIitt = result != null; // result != null here implies NOT_TRIAGEABLE
            String reviewReason = overIncompleteIitt
                    ? "Manual acuity recorded over an incomplete IITT assessment; unassessed inputs: "
                            + result.unassessedInputs()
                    : null;
            return new TriageDecision(true, explicitAcuity, manualTool(declaredSystem), null, null,
                    List.of(), List.of(), overIncompleteIitt ? result.unassessedInputs() : List.of(),
                    overIncompleteIitt, reviewReason, signMap(body), advisory, null, null);
        }

        if (result != null) {
            // IITT ran, returned NOT_TRIAGEABLE, and there is no explicit acuity to stand in.
            return TriageDecision.refused("not_triageable",
                    "IITT assessment is incomplete, so no triage priority can be assigned. "
                            + "Assess the listed inputs and repeat — this is not GREEN and not acuity 3.",
                    result.unassessedInputs());
        }

        return TriageDecision.refused("not_triageable",
                "No IITT assessment and no explicit acuity were supplied. ESI/MTS are advisory only and "
                        + "cannot set the acuity of record (product-owner ruling). Provide IITT signs and "
                        + "vitals with the patient's age, or an explicit clinician-entered acuity.",
                List.of());
    }

    /** Build three-valued triage facts from the request, aliasing the vital keys the UI may send. */
    static TriageFacts toFacts(Map<String, Object> body) {
        TriageFacts.Builder b = TriageFacts.builder();

        Integer ageDays = resolveAgeDays(body);
        if (ageDays != null) {
            b.ageDays(ageDays);
        }
        Boolean pregnant = asBool(body.get("pregnant"));
        if (pregnant != null) {
            b.pregnant(pregnant);
        }

        // Signs: only explicitly-recorded booleans; a key that is absent stays UNKNOWN, never false.
        for (Map.Entry<String, Object> e : signMap(body).entrySet()) {
            Boolean present = asBool(e.getValue());
            if (present != null) {
                b.sign(e.getKey(), present);
            }
        }

        // Vitals: alias common request keys onto the IITT vital codes; skip unparseable/absent values.
        Object rawVitals = body.get("vitals");
        if (rawVitals instanceof Map<?, ?> vitals) {
            for (Map.Entry<?, ?> e : vitals.entrySet()) {
                String code = VITAL_ALIASES.get(String.valueOf(e.getKey()).trim().toLowerCase(Locale.ROOT));
                if (code == null) {
                    continue;
                }
                Double value = IittChart.AVPU.equals(code) ? avpuToNumeric(e.getValue()) : asDouble(e.getValue());
                if (value != null) {
                    b.vital(code, value);
                }
            }
        }
        return b.build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> signMap(Map<String, Object> body) {
        for (String key : List.of("emergencySigns", "emergency_signs", "iittSigns", "iitt_signs")) {
            Object raw = body.get(key);
            if (raw instanceof Map<?, ?> m) {
                return new LinkedHashMap<>((Map<String, Object>) m);
            }
        }
        return Map.of();
    }

    /** Aliases from what a request might carry to the IITT vital codes. Lower-cased on lookup. */
    private static final Map<String, String> VITAL_ALIASES = Map.ofEntries(
            Map.entry("heart_rate", IittChart.HR), Map.entry("heartrate", IittChart.HR),
            Map.entry("hr", IittChart.HR), Map.entry("pulse", IittChart.HR),
            Map.entry("respiratory_rate", IittChart.RR), Map.entry("respiratoryrate", IittChart.RR),
            Map.entry("rr", IittChart.RR), Map.entry("resp_rate", IittChart.RR),
            Map.entry("temperature_c", IittChart.TEMP), Map.entry("temperature", IittChart.TEMP),
            Map.entry("temp", IittChart.TEMP), Map.entry("tempc", IittChart.TEMP),
            Map.entry("spo2_percent", IittChart.SPO2), Map.entry("spo2", IittChart.SPO2),
            Map.entry("sats", IittChart.SPO2), Map.entry("oxygen_saturation", IittChart.SPO2),
            Map.entry("avpu", IittChart.AVPU));

    private static Integer resolveAgeDays(Map<String, Object> body) {
        Integer days = asInt(body.get("ageDays"));
        if (days == null) {
            days = asInt(body.get("age_days"));
        }
        if (days != null) {
            return days;
        }
        Integer years = firstInt(body, "ageYears", "age_years", "age");
        Integer months = firstInt(body, "ageMonths", "age_months");
        if (years == null && months == null) {
            return null;
        }
        int total = 0;
        if (years != null) {
            total += years * 365;
        }
        if (months != null) {
            total += months * 30;
        }
        return total;
    }

    /** A→0 (alert); V/P/U and anything not alert → >0, matching the engine's {@code a > 0} test. */
    private static Double avpuToNumeric(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        String s = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        return switch (s.charAt(0)) {
            case 'A' -> 0.0;
            case 'V' -> 1.0;
            case 'P' -> 2.0;
            case 'U' -> 3.0;
            default -> 1.0;
        };
    }

    /**
     * A manually-entered acuity records the framework the clinician used by hand. It is never
     * derived from the ESI/MTS engine. IMPILO_5 remains representable here as the free-hand default
     * (its retirement into a countersigned override is a later wave); WHO_IITT is not a manual tool.
     */
    private static String manualTool(String declaredSystem) {
        if (declaredSystem == null) {
            return "IMPILO_5";
        }
        String s = declaredSystem.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "ESI", "MTS", "IMPILO_5" -> s;
            default -> "IMPILO_5";
        };
    }

    private static Integer firstInt(Map<String, Object> body, String... keys) {
        for (String k : keys) {
            Integer v = asInt(body.get(k));
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return Integer.valueOf(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Double asDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return Double.valueOf(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Boolean asBool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof String s) {
            if ("true".equalsIgnoreCase(s.trim())) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(s.trim())) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    private static String append(String base, String more) {
        return base == null ? more : base + " | " + more;
    }
}
