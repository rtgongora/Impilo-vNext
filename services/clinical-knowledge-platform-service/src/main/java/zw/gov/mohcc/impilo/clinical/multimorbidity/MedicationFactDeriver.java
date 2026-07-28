package zw.gov.mohcc.impilo.clinical.multimorbidity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives the medication-class facts that the deprescribing and renal-safety rules consume, from the
 * patient's actual medicine list rather than from a clinician's answer to a question.
 *
 * <p><strong>The defect this closes.</strong> {@code deprescribing-rules.json} gates on
 * {@code onMetformin}, {@code onNsaid}, {@code onNephrotoxin}, {@code onRenallyClearedDrug} and
 * {@code duplicateTherapyDetected}. Nothing in the estate produced any of them: the sole source was a
 * YES/NO dropdown on the medicine CDS page. {@code DEPRX_DUPLICATE_THERAPY_REVIEW} therefore fired
 * when a clinician had already noticed duplicate therapy and said so — it advised on a finding rather
 * than making one. The rules were never wrong; they were never fed.</p>
 *
 * <p><strong>Precedence.</strong> A fact derived from the medicine list wins over a caller-supplied
 * one, because the list is evidence and the answer is a recollection. Where derivation cannot decide
 * — the list was never obtained, or part of it could not be classified — any caller-supplied value is
 * preserved but labelled {@code CLINICIAN_ASSERTED}, so a consumer can tell a detection from a
 * recollection. It is never upgraded to look like a detection.</p>
 *
 * <p><strong>The safe direction.</strong> A negative ("this patient is not on an NSAID") is only
 * asserted when every medicine in the list was classifiable. If one medicine could not be recognised,
 * that medicine might be the NSAID, so the fact stays unknown and the rule declines to score. The
 * cost of an unknown is a question; the cost of a wrong negative is a renal-safety rule that clears a
 * patient it never checked.</p>
 */
@Component
public class MedicationFactDeriver {

    /** The fact key carrying the patient's current medicines, as {@code PatientFacts} emits it. */
    public static final String MEDICATION_LIST_FACT = "currentMedicationCodes";

    /** Provenance values reported alongside the derived facts. */
    public static final String DERIVED = "DERIVED_FROM_MEDICATION_LIST";
    public static final String ASSERTED = "CLINICIAN_ASSERTED";
    public static final String UNDETERMINED = "UNDETERMINED";

    private static final String YES = "YES";
    private static final String NO = "NO";

    private static final Logger log = LoggerFactory.getLogger(MedicationFactDeriver.class);

    private final MedicineClassifier classifier;

    public MedicationFactDeriver(MedicineClassifier classifier) {
        this.classifier = classifier;
    }

    /**
     * @param facts      the fact map to evaluate rules against, with derived values applied
     * @param provenance fact key to {@link #DERIVED}, {@link #ASSERTED} or {@link #UNDETERMINED}
     * @param note       a human-readable statement of what could and could not be derived, or null
     */
    public record Derivation(Map<String, Object> facts, Map<String, String> provenance, String note) {
    }

    public Derivation derive(Map<String, Object> incoming) {
        Map<String, Object> facts = new LinkedHashMap<>(incoming == null ? Map.of() : incoming);
        Map<String, String> provenance = new LinkedHashMap<>();

        List<String> medicines = medicineList(facts.get(MEDICATION_LIST_FACT));
        if (medicines == null) {
            // Absent is not empty. The list was never obtained, so nothing about the medicines is
            // known — including that there are none.
            for (String key : DerivedFact.keys()) {
                provenance.put(key, facts.containsKey(key) ? ASSERTED : UNDETERMINED);
            }
            return new Derivation(facts, provenance,
                    "The medicine list was not available, so no medication fact could be derived. Any "
                    + "medication answer below was supplied by a clinician, not detected.");
        }

        MedicineClassifier.Classification classification = classifier.classify(medicines);
        boolean canAssertNegative = classification.complete();

        for (DerivedFact fact : DerivedFact.values()) {
            Boolean present = fact.detect(classification, classifier);
            if (Boolean.TRUE.equals(present)) {
                facts.put(fact.key(), YES);
                provenance.put(fact.key(), DERIVED);
            } else if (canAssertNegative) {
                facts.put(fact.key(), NO);
                provenance.put(fact.key(), DERIVED);
            } else if (facts.containsKey(fact.key())) {
                provenance.put(fact.key(), ASSERTED);
            } else {
                provenance.put(fact.key(), UNDETERMINED);
            }
        }

        String note = note(classification, medicines.size());
        log.info("medication.facts.derived medicines={} classified={} unclassified={} negativesAsserted={}",
                medicines.size(), classification.byClass().size(), classification.unclassified().size(),
                canAssertNegative);
        return new Derivation(facts, provenance, note);
    }

    private String note(MedicineClassifier.Classification classification, int total) {
        if (!classification.contentLoaded()) {
            return "The medicine classification content could not be loaded, so no medication fact "
                   + "could be derived from the " + total + " medicine(s) on the list. This is a system "
                   + "fault, not a clinical finding.";
        }
        if (classification.unclassified().isEmpty()) {
            return null;
        }
        return classification.unclassified().size() + " of " + total + " medicine(s) could not be "
               + "recognised (" + String.join(", ", classification.unclassified()) + "), so this check "
               + "is partial: a medicine class can be confirmed present but cannot be ruled out.";
    }

    @SuppressWarnings("unchecked")
    private static List<String> medicineList(Object value) {
        if (value instanceof List<?> raw) {
            List<String> out = new ArrayList<>();
            for (Object o : raw) {
                if (o != null && !o.toString().isBlank()) {
                    out.add(o.toString());
                }
            }
            return out;
        }
        return null;
    }

    /** The five facts the deprescribing pack gates on, and how each is detected from the list. */
    private enum DerivedFact {
        ON_METFORMIN("onMetformin") {
            @Override
            Boolean detect(MedicineClassifier.Classification c, MedicineClassifier classifier) {
                return c.has("BIGUANIDE") ? Boolean.TRUE : null;
            }
        },
        ON_NSAID("onNsaid") {
            @Override
            Boolean detect(MedicineClassifier.Classification c, MedicineClassifier classifier) {
                return c.has("NSAID") ? Boolean.TRUE : null;
            }
        },
        ON_NEPHROTOXIN("onNephrotoxin") {
            @Override
            Boolean detect(MedicineClassifier.Classification c, MedicineClassifier classifier) {
                return anyClassWhere(c, classifier, MedicineClassifier.TherapeuticClass::nephrotoxic);
            }
        },
        ON_RENALLY_CLEARED_DRUG("onRenallyClearedDrug") {
            @Override
            Boolean detect(MedicineClassifier.Classification c, MedicineClassifier classifier) {
                return anyClassWhere(c, classifier, MedicineClassifier.TherapeuticClass::renallyCleared);
            }
        },
        DUPLICATE_THERAPY("duplicateTherapyDetected") {
            @Override
            Boolean detect(MedicineClassifier.Classification c, MedicineClassifier classifier) {
                for (MedicineClassifier.TherapeuticClass tc : classifier.classes()) {
                    // Insulin and opioids are excluded by content: a basal plus a prandial insulin, or a
                    // background plus a breakthrough opioid, are correct practice, not duplication.
                    if (tc.duplicateWithin() && c.countIn(tc.className()) >= 2) {
                        return Boolean.TRUE;
                    }
                }
                return null;
            }
        };

        private final String key;

        DerivedFact(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }

        /** @return TRUE when detected; null when not detected (which is not yet the same as absent) */
        abstract Boolean detect(MedicineClassifier.Classification c, MedicineClassifier classifier);

        static List<String> keys() {
            return java.util.Arrays.stream(values()).map(DerivedFact::key).toList();
        }

        static Boolean anyClassWhere(MedicineClassifier.Classification c, MedicineClassifier classifier,
                                     java.util.function.Predicate<MedicineClassifier.TherapeuticClass> p) {
            for (MedicineClassifier.TherapeuticClass tc : classifier.classes()) {
                if (p.test(tc) && c.has(tc.className())) {
                    return Boolean.TRUE;
                }
            }
            return null;
        }
    }
}
