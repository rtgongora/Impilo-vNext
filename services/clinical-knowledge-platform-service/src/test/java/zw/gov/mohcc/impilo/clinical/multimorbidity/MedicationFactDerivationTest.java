package zw.gov.mohcc.impilo.clinical.multimorbidity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The medication facts must be DETECTED from the patient's medicine list, not asserted by whoever is
 * looking at the screen.
 *
 * <p>Before this existed, {@code deprescribing-rules.json} gated on {@code onMetformin},
 * {@code onNsaid}, {@code onNephrotoxin}, {@code onRenallyClearedDrug} and
 * {@code duplicateTherapyDetected}, and the sole producer of all five was a YES/NO dropdown on the
 * medicine CDS page. {@code DEPRX_DUPLICATE_THERAPY_REVIEW} therefore fired only when a clinician had
 * already spotted duplicate therapy and said so — it advised on a finding rather than making one. A
 * rule that reports what it was told is not decision support.</p>
 */
class MedicationFactDerivationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MedicineClassifier classifier = new MedicineClassifier(objectMapper);
    private final MedicationFactDeriver deriver = new MedicationFactDeriver(classifier);

    private static Map<String, Object> facts(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i].toString(), kv[i + 1]);
        }
        return m;
    }

    @Test
    void theShippedClassificationContentLoads() {
        // If this fails every other assertion below is meaningless — an empty classification would
        // report everything unclassifiable, which is safe but useless.
        assertThat(classifier.contentLoaded()).isTrue();
        assertThat(classifier.classes()).isNotEmpty();
        assertThat(classifier.unsafeCombinations()).isNotEmpty();
    }

    @Test
    void duplicateTherapyIsDetectedFromTheListWithNobodyHavingBeenAsked() {
        // The whole point. Two ACE inhibitors, no dropdown value supplied, and the fact comes out YES.
        MedicationFactDeriver.Derivation d = deriver.derive(facts(
                "currentMedicationCodes", List.of("lisinopril", "enalapril")));

        assertThat(d.facts()).containsEntry("duplicateTherapyDetected", "YES");
        assertThat(d.provenance()).containsEntry("duplicateTherapyDetected", MedicationFactDeriver.DERIVED);
    }

    @Test
    void aClinicianSayingThereIsDuplicateTherapyIsOverruledByAListThatShowsNone() {
        // Evidence beats recollection. The clinician may be remembering a medicine that was stopped.
        MedicationFactDeriver.Derivation d = deriver.derive(facts(
                "currentMedicationCodes", List.of("lisinopril", "amlodipine"),
                "duplicateTherapyDetected", "YES"));

        assertThat(d.facts()).containsEntry("duplicateTherapyDetected", "NO");
        assertThat(d.provenance()).containsEntry("duplicateTherapyDetected", MedicationFactDeriver.DERIVED);
    }

    @Test
    void aClinicianSayingThereIsNoDuplicateIsOverruledByAListThatShowsOne() {
        // The dangerous direction, and the one a mutation test caught as uncovered: the clinician has
        // NOT spotted the duplicate — which is the whole reason to run a detector — and the detector
        // must not defer to them. A derived positive overwrites an asserted negative.
        MedicationFactDeriver.Derivation d = deriver.derive(facts(
                "currentMedicationCodes", List.of("lisinopril", "ramipril"),
                "duplicateTherapyDetected", "NO"));

        assertThat(d.facts()).containsEntry("duplicateTherapyDetected", "YES");
        assertThat(d.provenance()).containsEntry("duplicateTherapyDetected", MedicationFactDeriver.DERIVED);
    }

    @Test
    void anUnrecognisedMedicineBlocksEveryNegativeButNotThePositives() {
        // The safe direction: an unclassifiable medicine might BE the NSAID, so "not on an NSAID"
        // cannot be asserted. A positive found elsewhere in the list is still a positive.
        MedicationFactDeriver.Derivation d = deriver.derive(facts(
                "currentMedicationCodes", List.of("metformin", "ZW-LOCAL-99381")));

        assertThat(d.facts()).containsEntry("onMetformin", "YES");
        assertThat(d.provenance()).containsEntry("onMetformin", MedicationFactDeriver.DERIVED);
        // Not on an NSAID as far as we can see — but we cannot see all of it, so we do not say so.
        assertThat(d.facts()).doesNotContainKey("onNsaid");
        assertThat(d.provenance()).containsEntry("onNsaid", MedicationFactDeriver.UNDETERMINED);
        assertThat(d.note()).contains("ZW-LOCAL-99381").contains("partial");
    }

    @Test
    void whenDerivationCannotDecideAClinicianAnswerSurvivesButIsLabelledAsOne() {
        // We keep the clinician's answer — they may well be right, and discarding it would lose
        // safety information. We must never let it be mistaken for a detection.
        MedicationFactDeriver.Derivation d = deriver.derive(facts(
                "currentMedicationCodes", List.of("ZW-LOCAL-99381"),
                "onNsaid", "YES"));

        assertThat(d.facts()).containsEntry("onNsaid", "YES");
        assertThat(d.provenance()).containsEntry("onNsaid", MedicationFactDeriver.ASSERTED);
    }

    @Test
    void anAbsentMedicineListDerivesNothingAndSaysSo() {
        // Absent is not empty. OrosMedicationIntegration returns null when it could not ask, and a
        // null list must never become "this patient takes no medicines".
        MedicationFactDeriver.Derivation d = deriver.derive(facts("egfr", 25));

        assertThat(d.facts()).doesNotContainKey("onMetformin").doesNotContainKey("duplicateTherapyDetected");
        assertThat(d.provenance()).containsEntry("onMetformin", MedicationFactDeriver.UNDETERMINED);
        assertThat(d.note()).contains("was not available");
    }

    @Test
    void anEmptyMedicineListIsAnAnswerAndDoesAssertTheNegatives() {
        // An empty list is an affirmative statement that there are no medicines — different from a
        // list that could not be obtained, and the rules are entitled to act on it.
        MedicationFactDeriver.Derivation d = deriver.derive(facts(
                "currentMedicationCodes", List.of()));

        assertThat(d.facts()).containsEntry("onMetformin", "NO").containsEntry("onNsaid", "NO")
                .containsEntry("duplicateTherapyDetected", "NO");
        assertThat(d.provenance()).containsEntry("onMetformin", MedicationFactDeriver.DERIVED);
    }

    @Test
    void twoInsulinsAreNotDuplicateTherapy() {
        // Basal plus prandial insulin is correct practice. A duplicate-therapy detector that flags it
        // trains clinicians to dismiss the alert, which is how a real duplicate gets dismissed too.
        MedicationFactDeriver.Derivation d = deriver.derive(facts(
                "currentMedicationCodes", List.of("insulin glargine", "insulin aspart")));

        assertThat(d.facts()).containsEntry("duplicateTherapyDetected", "NO");
    }

    @Test
    void aBackgroundAndBreakthroughOpioidAreNotDuplicateTherapy() {
        // Brief §8.13 — standard palliative practice.
        MedicationFactDeriver.Derivation d = deriver.derive(facts(
                "currentMedicationCodes", List.of("morphine sulfate MR", "morphine immediate release")));

        assertThat(d.facts()).containsEntry("duplicateTherapyDetected", "NO");
    }

    @Test
    void anAtcCodeClassifiesJustAsAGenericNameDoes() {
        // C09AA02 is enalapril, C09AA05 ramipril — two ACE inhibitors expressed only as codes.
        MedicationFactDeriver.Derivation d = deriver.derive(facts(
                "currentMedicationCodes", List.of("C09AA02", "C09AA05")));

        assertThat(d.facts()).containsEntry("duplicateTherapyDetected", "YES");
    }

    @Test
    void nephrotoxicAndRenallyClearedAreReadFromTheClassAndNotFromTheName() {
        MedicationFactDeriver.Derivation d = deriver.derive(facts(
                "currentMedicationCodes", List.of("ibuprofen", "metformin")));

        assertThat(d.facts()).containsEntry("onNsaid", "YES")
                .containsEntry("onNephrotoxin", "YES")
                .containsEntry("onRenallyClearedDrug", "YES")
                .containsEntry("onMetformin", "YES");
    }

    @Test
    void aPatientOnNeitherGetsCleanNegatives() {
        MedicationFactDeriver.Derivation d = deriver.derive(facts(
                "currentMedicationCodes", List.of("amlodipine", "simvastatin")));

        assertThat(d.facts()).containsEntry("onNsaid", "NO")
                .containsEntry("onNephrotoxin", "NO")
                .containsEntry("onMetformin", "NO")
                .containsEntry("duplicateTherapyDetected", "NO");
        assertThat(d.note()).isNull();
    }

    @Test
    void missingClassificationContentReportsASystemFaultRatherThanACleanList() {
        // A classifier pointed at nothing must not look like a patient on nothing.
        MedicineClassifier broken = new MedicineClassifier(objectMapper) {
            @Override
            public MedicineClassifier.Classification classify(List<String> medicines) {
                return new MedicineClassifier.Classification(Map.of(),
                        medicines == null ? List.of() : List.copyOf(medicines), false);
            }
        };
        MedicationFactDeriver.Derivation d = new MedicationFactDeriver(broken).derive(facts(
                "currentMedicationCodes", List.of("ibuprofen")));

        assertThat(d.facts()).doesNotContainKey("onNsaid");
        assertThat(d.note()).contains("could not be loaded").contains("system fault");
    }
}
