package zw.gov.mohcc.impilo.medicine.pharm;

import java.util.Locale;
import java.util.Optional;

/**
 * Renal dosing awareness for adult medicine pharmacology (Wave 5.5).
 *
 * <p>Given an eGFR and a therapeutic drug-class key, returns whether dose adjustment or caution is
 * warranted. This is <strong>awareness</strong>, not a dose calculator: it names the band and the
 * action class, not milligrams. Interaction checking and formulary lookup are out of scope.</p>
 *
 * <p><strong>Never invents renal function.</strong> A missing eGFR yields {@link Level#UNKNOWN} with
 * an explicit message — the same discipline as {@link zw.gov.mohcc.impilo.medicine.renal.EgfrCalculator}.</p>
 *
 * <p>Thresholds align with the deprescribing engineering seed (eGFR 30 and 60 bands) pending MoHCC
 * ratification of a national renal-dosing schedule. Primary sources are not vendored under
 * {@code docs/reference}.</p>
 */
public final class RenalDosingAdvisor {

    public static final String CONTENT_VERSION = "impilo-renal-dosing-1.0.0";

    /** The advisory level returned for a drug class at a given eGFR. */
    public enum Level {
        ADJUST,
        CAUTION,
        NO_CHANGE,
        UNKNOWN
    }

    private RenalDosingAdvisor() {
    }

    /**
     * Advises on renal dosing for one drug class at one eGFR.
     *
     * @param egfr         estimated GFR in mL/min/1.73m²; null means unknown — never defaulted
     * @param drugClassKey therapeutic class key (e.g. {@code BIGUANIDE}, {@code NSAID}); case-insensitive
     */
    public static Advice advise(Double egfr, String drugClassKey) {
        if (egfr == null) {
            return Advice.unknown(drugClassKey, null,
                    "eGFR is not available — renal dosing advice cannot be given without measured or"
                            + " derived renal function.");
        }
        if (egfr <= 0 || !Double.isFinite(egfr)) {
            return Advice.unknown(drugClassKey, egfr,
                    "The supplied eGFR is not a usable value — renal dosing advice cannot be given.");
        }
        Optional<DrugClassRule> rule = DrugClassRule.parse(drugClassKey);
        if (rule.isEmpty()) {
            return Advice.unknown(drugClassKey, egfr,
                    "The drug class '" + sanitise(drugClassKey)
                            + "' is not recognised — renal dosing advice cannot be given.");
        }
        return rule.get().evaluate(egfr);
    }

    private static String sanitise(String raw) {
        if (raw == null || raw.isBlank()) {
            return "(none)";
        }
        return raw.trim();
    }

    /** One drug class's renal-dosing bands. Keys mirror {@code clinical/medicine-classification.json}. */
    enum DrugClassRule {
        BIGUANIDE("BIGUANIDE", "Biguanide (metformin)") {
            @Override
            Advice evaluate(double egfr) {
                if (egfr < 30) {
                    return adjust(egfr,
                            "Metformin is contraindicated at eGFR below 30 mL/min/1.73m² — stop rather"
                                    + " than reduce the dose (lactic-acidosis risk).");
                }
                if (egfr < 45) {
                    return caution(egfr,
                            "Metformin needs caution at eGFR 30–44 mL/min/1.73m² — use the lowest effective"
                                    + " dose and recheck renal function frequently.");
                }
                return noChange(egfr,
                        "No routine metformin dose adjustment at this eGFR — continue usual monitoring.");
            }
        },
        NSAID("NSAID", "Non-steroidal anti-inflammatory") {
            @Override
            Advice evaluate(double egfr) {
                if (egfr < 30) {
                    return adjust(egfr,
                            "NSAIDs should be avoided at eGFR below 30 mL/min/1.73m² — stop and choose a"
                                    + " non-nephrotoxic analgesic.");
                }
                if (egfr < 60) {
                    return caution(egfr,
                            "NSAIDs need caution at eGFR below 60 mL/min/1.73m² — use the lowest dose for"
                                    + " the shortest time and monitor renal function.");
                }
                return noChange(egfr,
                        "No routine NSAID dose adjustment at this eGFR — still avoid in dehydration or"
                                + " with ACE inhibitor/diuretic combinations.");
            }
        },
        AMINOGLYCOSIDE("AMINOGLYCOSIDE", "Aminoglycoside antibiotic") {
            @Override
            Advice evaluate(double egfr) {
                if (egfr < 30) {
                    return adjust(egfr,
                            "Aminoglycosides are nephrotoxic and renally cleared — at eGFR below 30"
                                    + " mL/min/1.73m² dose adjustment or an alternative is required with"
                                    + " level monitoring.");
                }
                if (egfr < 60) {
                    return caution(egfr,
                            "Aminoglycosides need extended-interval dosing and level monitoring at eGFR"
                                    + " below 60 mL/min/1.73m².");
                }
                return caution(egfr,
                        "Aminoglycosides remain nephrotoxic even with normal eGFR — monitor levels and"
                                + " renal function during treatment.");
            }
        },
        ACE_INHIBITOR("ACE_INHIBITOR", "ACE inhibitor") {
            @Override
            Advice evaluate(double egfr) {
                return renallyClearedClass(egfr, displayName());
            }
        },
        ARB("ARB", "Angiotensin receptor blocker") {
            @Override
            Advice evaluate(double egfr) {
                if (egfr < 30) {
                    return caution(egfr,
                            displayName() + " may still be indicated for cardiorenal protection at low eGFR"
                                    + " — start low, titrate carefully and monitor potassium and creatinine.");
                }
                if (egfr < 60) {
                    return caution(egfr,
                            displayName() + " needs careful titration at eGFR below 60 mL/min/1.73m² —"
                                    + " check potassium and renal function after any dose change.");
                }
                return noChange(egfr,
                        "No routine " + displayName() + " dose adjustment at this eGFR — monitor as usual.");
            }
        },
        SULFONYLUREA("SULFONYLUREA", "Sulfonylurea") {
            @Override
            Advice evaluate(double egfr) {
                return renallyClearedClass(egfr, displayName());
            }
        },
        POTASSIUM_SPARING_DIURETIC("POTASSIUM_SPARING_DIURETIC", "Potassium-sparing diuretic") {
            @Override
            Advice evaluate(double egfr) {
                return renallyClearedClass(egfr, displayName());
            }
        },
        ANTICOAGULANT("ANTICOAGULANT", "Anticoagulant") {
            @Override
            Advice evaluate(double egfr) {
                return renallyClearedClass(egfr, displayName());
            }
        },
        OPIOID("OPIOID", "Opioid analgesic") {
            @Override
            Advice evaluate(double egfr) {
                if (egfr < 30) {
                    return adjust(egfr,
                            "Many opioids and their active metabolites accumulate at eGFR below 30"
                                    + " mL/min/1.73m² — reduce dose and extend interval; avoid morphine if"
                                    + " alternatives exist.");
                }
                if (egfr < 60) {
                    return caution(egfr,
                            "Opioids may need dose reduction at eGFR below 60 mL/min/1.73m² — titrate to"
                                    + " effect and watch for sedation.");
                }
                return noChange(egfr,
                        "No routine opioid dose adjustment at this eGFR — titrate to clinical effect.");
            }
        },
        STATIN("STATIN", "Statin") {
            @Override
            Advice evaluate(double egfr) {
                return noRenalRoutine(egfr);
            }
        },
        PPI("PPI", "Proton-pump inhibitor") {
            @Override
            Advice evaluate(double egfr) {
                return noRenalRoutine(egfr);
            }
        },
        BETA_BLOCKER("BETA_BLOCKER", "Beta-blocker") {
            @Override
            Advice evaluate(double egfr) {
                return noRenalRoutine(egfr);
            }
        },
        CALCIUM_CHANNEL_BLOCKER("CALCIUM_CHANNEL_BLOCKER", "Calcium-channel blocker") {
            @Override
            Advice evaluate(double egfr) {
                return noRenalRoutine(egfr);
            }
        },
        THIAZIDE("THIAZIDE", "Thiazide diuretic") {
            @Override
            Advice evaluate(double egfr) {
                if (egfr < 30) {
                    return adjust(egfr,
                            "Thiazide diuretics are ineffective at eGFR below 30 mL/min/1.73m² — switch"
                                    + " to a loop diuretic.");
                }
                if (egfr < 60) {
                    return caution(egfr,
                            "Thiazide diuretics lose efficacy as eGFR falls — consider a loop diuretic if"
                                    + " diuresis is inadequate.");
                }
                return noChange(egfr, "No routine thiazide dose adjustment at this eGFR.");
            }
        },
        LOOP_DIURETIC("LOOP_DIURETIC", "Loop diuretic") {
            @Override
            Advice evaluate(double egfr) {
                if (egfr < 30) {
                    return caution(egfr,
                            "Loop diuretic doses often need increasing as eGFR falls — monitor response and"
                                    + " electrolytes.");
                }
                return noChange(egfr, "No fixed loop-diuretic adjustment at this eGFR — titrate to effect.");
            }
        };

        private final String key;
        private final String display;

        DrugClassRule(String key, String display) {
            this.key = key;
            this.display = display;
        }

        String displayName() {
            return display;
        }

        abstract Advice evaluate(double egfr);

        static Optional<DrugClassRule> parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            String normalised = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            for (DrugClassRule rule : values()) {
                if (rule.key.equals(normalised)) {
                    return Optional.of(rule);
                }
            }
            return Optional.empty();
        }

        Advice adjust(double egfr, String message) {
            return Advice.of(Level.ADJUST, key, display, egfr, message);
        }

        Advice caution(double egfr, String message) {
            return Advice.of(Level.CAUTION, key, display, egfr, message);
        }

        Advice noChange(double egfr, String message) {
            return Advice.of(Level.NO_CHANGE, key, display, egfr, message);
        }

        Advice renallyClearedClass(double egfr, String name) {
            if (egfr < 60) {
                return adjust(egfr,
                        name + " is mainly renally cleared — review dose or interval against a renal-dosing"
                                + " reference at eGFR below 60 mL/min/1.73m².");
            }
            if (egfr < 90) {
                return caution(egfr,
                        name + " may need dose review at eGFR below 90 mL/min/1.73m² — check against a"
                                + " renal-dosing reference if toxicity is a concern.");
            }
            return noChange(egfr,
                    "No routine " + name + " dose adjustment at this eGFR — continue usual monitoring.");
        }

        Advice noRenalRoutine(double egfr) {
            return noChange(egfr,
                    "No routine renal dose adjustment for " + display + " at this eGFR.");
        }
    }

    /**
     * One renal-dosing advisory result.
     *
     * @param level           {@link Level#UNKNOWN} when eGFR or drug class is missing/unrecognised
     * @param drugClass       normalised class key, or the raw key when unknown
     * @param drugClassDisplay human-readable class name when recognised
     * @param egfr            the eGFR used; null when unknown
     * @param message         clinician-readable explanation
     * @param contentVersion  governed content version
     */
    public record Advice(Level level,
                         String drugClass,
                         String drugClassDisplay,
                         Double egfr,
                         String message,
                         String contentVersion) {

        public boolean computed() {
            return level != Level.UNKNOWN;
        }

        static Advice of(Level level, String drugClass, String display, Double egfr, String message) {
            return new Advice(level, drugClass, display, egfr, message, CONTENT_VERSION);
        }

        static Advice unknown(String drugClassKey, Double egfr, String message) {
            String key = drugClassKey == null || drugClassKey.isBlank()
                    ? null
                    : drugClassKey.trim().toUpperCase(Locale.ROOT);
            return new Advice(Level.UNKNOWN, key, null, egfr, message, CONTENT_VERSION);
        }
    }
}
