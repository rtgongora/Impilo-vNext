package zw.gov.mohcc.impilo.clinical.multimorbidity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityContext.Appointment;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityContext.CareTeamMember;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityContext.Condition;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityContext.Investigation;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityView.DetectionState;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MultimorbidityView.PanelState;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Brief §9 — eleven panels, seven detections, and the property that makes them safe to read.
 *
 * <p>The property under test throughout: <strong>a detection reports NOT_DETECTED only when it had
 * every input it needed.</strong> This view exists because single-disease screens hide cross-disease
 * harm. A view that answered "nothing found" when it could not look would hide that harm more
 * convincingly than the screens it replaces, because it carries the authority of having checked.</p>
 */
class MultimorbidityEngineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 28);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MultimorbidityContent content = new MultimorbidityContent(objectMapper);
    private final MedicineClassifier classifier = new MedicineClassifier(objectMapper);
    private final MultimorbidityEngine engine = new MultimorbidityEngine(content, classifier);

    private static MultimorbidityContext ctx(List<Condition> conditions, List<String> meds,
                                             List<Appointment> appts, List<Investigation> invs) {
        return new MultimorbidityContext(conditions, meds, appts, invs, null, null, null, null, null);
    }

    private static Condition dx(String code, String display) {
        return new Condition(code, display, true);
    }

    private MultimorbidityView.Detection detection(MultimorbidityView view, String key) {
        return view.detections().stream().filter(d -> d.key().equals(key)).findFirst().orElseThrow();
    }

    private MultimorbidityView.Panel panel(MultimorbidityView view, String key) {
        return view.panels().stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void theViewHasAllElevenPanelsAndAllSevenDetections() {
        // §9 names eleven things the view must show and seven the system must detect. A view that
        // silently ships nine of eleven looks complete to everyone who did not count.
        MultimorbidityView view = engine.assess(MultimorbidityContext.empty(), TODAY);
        assertThat(view.panels()).hasSize(11);
        assertThat(view.detections()).hasSize(7);
        assertThat(view.detections()).extracting(MultimorbidityView.Detection::key)
                .containsExactlyInAnyOrder("duplicate_medicines", "contradictory_targets",
                        "unsafe_combinations", "excessive_visit_schedule", "repeated_investigations",
                        "competing_dietary_advice", "high_treatment_burden");
    }

    @Test
    void anEmptyContextAnswersNothingAndSaysSoOnEveryDetection() {
        // The single most important assertion in this file. Nothing was obtained, so nothing was
        // checked, and not one detection is allowed to read as clear.
        MultimorbidityView view = engine.assess(MultimorbidityContext.empty(), TODAY);

        assertThat(view.detections()).allMatch(d -> d.state() == DetectionState.UNDETERMINED);
        assertThat(view.incomplete()).isTrue();
        assertThat(view.completenessNote()).contains("An unanswered check is not a clear one.");
    }

    @Test
    void anUnreadableProblemListIsNotAPatientWithNoProblems() {
        MultimorbidityView view = engine.assess(
                ctx(null, List.of("amlodipine"), List.of(), List.of()), TODAY);

        assertThat(panel(view, "active_conditions").state()).isEqualTo(PanelState.UNKNOWN);
        assertThat(panel(view, "active_conditions").unknownReason()).contains("could not look");
        assertThat(detection(view, "contradictory_targets").state()).isEqualTo(DetectionState.UNDETERMINED);
    }

    @Test
    void anUnreadableAppointmentBookIsNotAPatientWithNoAppointments() {
        MultimorbidityView view = engine.assess(
                ctx(List.of(dx("I10", "Hypertension")), List.of(), null, List.of()), TODAY);

        assertThat(panel(view, "appointment_burden").state()).isEqualTo(PanelState.UNKNOWN);
        assertThat(detection(view, "excessive_visit_schedule").state()).isEqualTo(DetectionState.UNDETERMINED);
        // Burden is a sum; missing one term understates it, and understating leaves the patient
        // carrying what nobody counted.
        assertThat(detection(view, "high_treatment_burden").state()).isEqualTo(DetectionState.UNDETERMINED);
        assertThat(detection(view, "high_treatment_burden").undeterminedReason()).contains("understate");
    }

    @Test
    void duplicateTherapyIsDetectedAcrossTwoMembersOfOneClass() {
        MultimorbidityView view = engine.assess(
                ctx(List.of(), List.of("lisinopril", "ramipril"), List.of(), List.of()), TODAY);

        MultimorbidityView.Detection d = detection(view, "duplicate_medicines");
        assertThat(d.state()).isEqualTo(DetectionState.DETECTED);
        assertThat(d.findings()).hasSize(1);
        assertThat(d.findings().get(0).message()).contains("lisinopril").contains("ramipril");
        assertThat(d.findings().get(0).requiredAction()).isNotBlank();
    }

    @Test
    void anUnrecognisedMedicineLeavesDuplicationUndeterminedRatherThanClear() {
        MultimorbidityView view = engine.assess(
                ctx(List.of(), List.of("amlodipine", "ZW-LOCAL-1"), List.of(), List.of()), TODAY);

        MultimorbidityView.Detection d = detection(view, "duplicate_medicines");
        assertThat(d.state()).isEqualTo(DetectionState.UNDETERMINED);
        assertThat(d.undeterminedReason()).contains("cannot be ruled out");
    }

    @Test
    void aFullyRecognisedListWithNoDuplicatesIsAllowedToSayClear() {
        // The reassuring answer must remain reachable, or the view becomes noise and gets ignored.
        MultimorbidityView view = engine.assess(
                ctx(List.of(), List.of("amlodipine", "simvastatin"), List.of(), List.of()), TODAY);

        assertThat(detection(view, "duplicate_medicines").state()).isEqualTo(DetectionState.NOT_DETECTED);
    }

    @Test
    void theTripleWhammyIsDetectedThroughItsAlternativeClassCombination() {
        // NSAID + thiazide + ARB is not the primary triple in content; it is one of alsoSatisfiedBy,
        // and the alternatives are where a combination detector usually fails silently.
        MultimorbidityView view = engine.assess(
                ctx(List.of(), List.of("ibuprofen", "hydrochlorothiazide", "losartan"), List.of(), List.of()), TODAY);

        MultimorbidityView.Detection d = detection(view, "unsafe_combinations");
        assertThat(d.state()).isEqualTo(DetectionState.DETECTED);
        assertThat(d.findings()).extracting(MultimorbidityView.Finding::code)
                .contains("MM_COMBO_TRIPLE_WHAMMY");
    }

    @Test
    void twoAnticoagulantsAreDetectedByCountRatherThanBySetMembership() {
        MultimorbidityView view = engine.assess(
                ctx(List.of(), List.of("warfarin", "rivaroxaban"), List.of(), List.of()), TODAY);

        assertThat(detection(view, "unsafe_combinations").findings())
                .extracting(MultimorbidityView.Finding::code).contains("MM_COMBO_DUAL_ANTICOAGULANT");
    }

    @Test
    void theCkdAndDiabetesDietConflictIsDetected() {
        MultimorbidityView view = engine.assess(
                ctx(List.of(dx("N18.3", "Chronic kidney disease stage 3"), dx("E11", "Type 2 diabetes")),
                        List.of(), List.of(), List.of()), TODAY);

        MultimorbidityView.Detection d = detection(view, "competing_dietary_advice");
        assertThat(d.state()).isEqualTo(DetectionState.DETECTED);
        assertThat(d.findings()).extracting(MultimorbidityView.Finding::code).contains("MM_DIET_CKD_VS_DIABETES");
    }

    @Test
    void aDietConflictDoesNotLeakIntoTheContradictoryTargetsDetection() {
        // The two detections are separate §9 requirements; folding one into the other would report
        // seven detections while running six.
        MultimorbidityView view = engine.assess(
                ctx(List.of(dx("N18.3", "CKD stage 3"), dx("E11", "Type 2 diabetes")),
                        List.of(), List.of(), List.of()), TODAY);

        assertThat(detection(view, "contradictory_targets").findings())
                .extracting(MultimorbidityView.Finding::code).doesNotContain("MM_DIET_CKD_VS_DIABETES");
    }

    @Test
    void theFrailtyAndGlycaemicTargetConflictIsDetected() {
        MultimorbidityView view = engine.assess(
                ctx(List.of(dx("E11", "Type 2 diabetes"), dx("R54", "Frailty")),
                        List.of(), List.of(), List.of()), TODAY);

        assertThat(detection(view, "contradictory_targets").findings())
                .extracting(MultimorbidityView.Finding::code).contains("MM_GLYCAEMIC_TARGET_VS_FRAILTY");
    }

    @Test
    void oneConditionAloneNeverRaisesAConflict() {
        MultimorbidityView view = engine.assess(
                ctx(List.of(dx("E11", "Type 2 diabetes")), List.of(), List.of(), List.of()), TODAY);

        assertThat(detection(view, "contradictory_targets").state()).isEqualTo(DetectionState.NOT_DETECTED);
        assertThat(detection(view, "competing_dietary_advice").state()).isEqualTo(DetectionState.NOT_DETECTED);
    }

    @Test
    void separateAttendancesAreCountedButSameDayOnesAreNot() {
        // Two clinics on one day is one trip for the patient. Counting it twice would penalise the
        // co-ordination this detector exists to ask for.
        List<Appointment> sameDay = List.of(
                new Appointment(TODAY.minusDays(5), "Diabetes", "review"),
                new Appointment(TODAY.minusDays(5), "Renal", "review"),
                new Appointment(TODAY.minusDays(10), "Cardiology", "review"));
        assertThat(detection(engine.assess(ctx(List.of(), List.of(), sameDay, List.of()), TODAY),
                "excessive_visit_schedule").state()).isEqualTo(DetectionState.NOT_DETECTED);

        List<Appointment> separate = List.of(
                new Appointment(TODAY.minusDays(5), "Diabetes", "review"),
                new Appointment(TODAY.minusDays(12), "Renal", "review"),
                new Appointment(TODAY.minusDays(20), "Cardiology", "review"),
                new Appointment(TODAY.minusDays(30), "Eye", "screening"));
        assertThat(detection(engine.assess(ctx(List.of(), List.of(), separate, List.of()), TODAY),
                "excessive_visit_schedule").state()).isEqualTo(DetectionState.DETECTED);
    }

    @Test
    void anAppointmentOutsideTheWindowDoesNotCount() {
        List<Appointment> old = List.of(
                new Appointment(TODAY.minusDays(200), "Diabetes", "review"),
                new Appointment(TODAY.minusDays(210), "Renal", "review"),
                new Appointment(TODAY.minusDays(220), "Cardiology", "review"),
                new Appointment(TODAY.minusDays(230), "Eye", "screening"));

        assertThat(detection(engine.assess(ctx(List.of(), List.of(), old, List.of()), TODAY),
                "excessive_visit_schedule").state()).isEqualTo(DetectionState.NOT_DETECTED);
    }

    @Test
    void anHba1cRepeatedInsideItsBiologicalIntervalIsDetected() {
        List<Investigation> invs = List.of(
                new Investigation("HBA1C", TODAY.minusDays(30), "Diabetes clinic"),
                new Investigation("HBA1C", TODAY.minusDays(10), "Medical outpatients"));

        MultimorbidityView.Detection d = detection(
                engine.assess(ctx(List.of(), List.of(), List.of(), invs), TODAY), "repeated_investigations");
        assertThat(d.state()).isEqualTo(DetectionState.DETECTED);
        assertThat(d.findings().get(0).explanation()).contains("Diabetes clinic").contains("Medical outpatients");
    }

    @Test
    void anHba1cRepeatedAfterItsIntervalIsNotAFinding() {
        List<Investigation> invs = List.of(
                new Investigation("HBA1C", TODAY.minusDays(200), "Diabetes clinic"),
                new Investigation("HBA1C", TODAY.minusDays(10), "Diabetes clinic"));

        assertThat(detection(engine.assess(ctx(List.of(), List.of(), List.of(), invs), TODAY),
                "repeated_investigations").state()).isEqualTo(DetectionState.NOT_DETECTED);
    }

    @Test
    void aRepeatOfATestWithNoGovernedIntervalIsUndeterminedRatherThanClear() {
        // We can see it was repeated and have no basis to say whether that was premature. Saying
        // "no repeats" would be a claim the content cannot support.
        List<Investigation> invs = List.of(
                new Investigation("SOME_LOCAL_ASSAY", TODAY.minusDays(3), "Ward"),
                new Investigation("SOME_LOCAL_ASSAY", TODAY.minusDays(1), "Ward"));

        MultimorbidityView.Detection d = detection(
                engine.assess(ctx(List.of(), List.of(), List.of(), invs), TODAY), "repeated_investigations");
        assertThat(d.state()).isEqualTo(DetectionState.UNDETERMINED);
        assertThat(d.undeterminedReason()).contains("SOME_LOCAL_ASSAY");
    }

    @Test
    void treatmentBurdenSumsMedicinesVisitsAndConditions() {
        List<Condition> conditions = List.of(
                dx("E11", "Type 2 diabetes"), dx("I10", "Hypertension"),
                dx("N18.3", "CKD stage 3"), dx("J44", "COPD"), dx("I50", "Heart failure"));
        List<String> meds = List.of("metformin", "amlodipine", "simvastatin", "furosemide",
                "bisoprolol", "aspirin", "omeprazole", "salbutamol", "insulin glargine", "spironolactone");
        List<Appointment> appts = List.of(
                new Appointment(TODAY.minusDays(3), "Diabetes", null),
                new Appointment(TODAY.minusDays(13), "Renal", null),
                new Appointment(TODAY.minusDays(23), "Cardiology", null),
                new Appointment(TODAY.minusDays(33), "Respiratory", null),
                new Appointment(TODAY.minusDays(43), "Medical", null),
                new Appointment(TODAY.minusDays(53), "Eye", null));

        MultimorbidityView.Detection d = detection(
                engine.assess(ctx(conditions, meds, appts, List.of()), TODAY), "high_treatment_burden");
        assertThat(d.state()).isEqualTo(DetectionState.DETECTED);
        assertThat(d.findings().get(0).message()).contains("10 medicine(s)");
    }

    @Test
    void aLightlyTreatedPatientIsNotFlaggedForBurden() {
        MultimorbidityView view = engine.assess(ctx(
                List.of(dx("I10", "Hypertension")), List.of("amlodipine"),
                List.of(new Appointment(TODAY.minusDays(10), "Medical", null)), List.of()), TODAY);

        assertThat(detection(view, "high_treatment_burden").state()).isEqualTo(DetectionState.NOT_DETECTED);
    }

    @Test
    void hepaticFunctionIsReportedUnknownRatherThanNormal() {
        // No governed hepatic-impairment instrument exists, so this is null for every real patient.
        // Rendering a blank would read as "no hepatic constraint".
        MultimorbidityView view = engine.assess(MultimorbidityContext.empty(), TODAY);

        assertThat(panel(view, "renal_hepatic_constraints").entries())
                .anyMatch(e -> e.contains("unknown rather than normal"));
    }

    @Test
    void unaskedPatientPrioritiesAreNamedAsUnasked() {
        // §9 requires a person-centred prioritised plan. It cannot be person-centred if nobody asked.
        MultimorbidityView view = engine.assess(MultimorbidityContext.empty(), TODAY);

        assertThat(panel(view, "patient_priorities").state()).isEqualTo(PanelState.UNKNOWN);
        assertThat(panel(view, "patient_priorities").unknownReason()).contains("has not been asked");
    }

    @Test
    void anUnnamedCareTeamIsCalledOutBecauseEveryoneThenAssumesSomebodyElse() {
        MultimorbidityView view = engine.assess(MultimorbidityContext.empty(), TODAY);

        assertThat(panel(view, "care_team").state()).isEqualTo(PanelState.UNKNOWN);
        assertThat(panel(view, "care_team").unknownReason()).contains("somebody else");
    }

    @Test
    void aFullyPopulatedContextCanReachACompleteView() {
        // If a complete view were unreachable the incomplete banner would be permanent, and a
        // permanent warning is one nobody reads.
        MultimorbidityContext full = new MultimorbidityContext(
                List.of(dx("I10", "Hypertension")),
                List.of("amlodipine"),
                List.of(new Appointment(TODAY.minusDays(10), "Medical", "review")),
                List.of(new Investigation("HBA1C", TODAY.minusDays(120), "Medical")),
                72.0, "NONE",
                List.of("Independent in all activities"),
                List.of("Wants to keep working"),
                List.of(new CareTeamMember("Medical officer", "Dr N", "Hypertension")));

        MultimorbidityView view = engine.assess(full, TODAY);

        assertThat(view.panels()).allMatch(p -> p.state() == PanelState.KNOWN);
        assertThat(view.detections()).allMatch(d -> d.state() == DetectionState.NOT_DETECTED);
        assertThat(view.incomplete()).isFalse();
        assertThat(view.completenessNote()).isNull();
    }

    @Test
    void theShippedContentLoadsAndIsMarkedAnEngineeringSeed() {
        assertThat(content.isLoaded()).isTrue();
        assertThat(content.conflicts()).isNotEmpty();
        assertThat(content.repeatIntervals()).isNotEmpty();
        assertThat(content.approvalStatus()).isEqualTo("ENGINEERING_SEED");
    }

    @Test
    void everyConflictInContentNamesANextStep() {
        // A finding without a required action is a notification, not decision support — the same rule
        // RuleContentLoader enforces on the tabular packs.
        assertThat(content.conflicts()).allSatisfy(c -> {
            assertThat(c.message()).isNotBlank();
            assertThat(c.requiredAction()).isNotBlank();
            assertThat(c.explanation()).isNotBlank();
        });
    }
}
