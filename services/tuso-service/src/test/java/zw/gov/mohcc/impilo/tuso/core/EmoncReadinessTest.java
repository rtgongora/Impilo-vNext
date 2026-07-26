package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tuso.core.EmoncReadinessService.Classification;
import zw.gov.mohcc.impilo.tuso.core.EmoncReadinessService.SignalFunctionStatus;
import zw.gov.mohcc.impilo.tuso.core.EmoncReadinessService.Status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EmONC readiness guard tests.
 *
 * <p>The single property under test everywhere here: <b>absence of evidence is not evidence of
 * absence.</b> An unassessed facility must classify as {@code INSUFFICIENT_EVIDENCE}, never as
 * "not capable" — because a midwife told a facility cannot perform a caesarean will drive past the
 * theatre that could have helped, and she will not question a confident answer.</p>
 */
class EmoncReadinessTest {

    private static final List<String> BASIC = List.of(
            "PARENTERAL_ANTIBIOTICS", "PARENTERAL_UTEROTONICS", "PARENTERAL_ANTICONVULSANTS",
            "MANUAL_REMOVAL_PLACENTA", "REMOVAL_RETAINED_PRODUCTS", "ASSISTED_VAGINAL_DELIVERY",
            "NEONATAL_RESUSCITATION");
    private static final List<String> COMPREHENSIVE = List.of("CAESAREAN_SECTION", "BLOOD_TRANSFUSION");

    private static SignalFunctionStatus sf(String code, String tier, Status status) {
        return new SignalFunctionStatus(code, code, tier, 1, status, null, null,
                status == Status.UNKNOWN ? null : Instant.now(), "SUPERVISORY_VISIT", 1);
    }

    private static List<SignalFunctionStatus> all(Status basic, Status comprehensive) {
        List<SignalFunctionStatus> out = new ArrayList<>();
        BASIC.forEach(c -> out.add(sf(c, "BASIC", basic)));
        COMPREHENSIVE.forEach(c -> out.add(sf(c, "COMPREHENSIVE", comprehensive)));
        return out;
    }

    // ── the invariant ───────────────────────────────────────────────────────

    /** A facility nobody has assessed is unknown, not incapable. This is the whole design. */
    @Test
    void anUnassessedFacilityIsInsufficientEvidenceAndNeverNeither() {
        Classification result = EmoncReadinessService.classify(all(Status.UNKNOWN, Status.UNKNOWN));
        assertThat(result).isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
        assertThat(result).isNotEqualTo(Classification.NEITHER);
    }

    /** One unknown among eight confirmed is still not a verdict. */
    @Test
    void aSingleUnknownFunctionBlocksAnyPositiveClassification() {
        List<SignalFunctionStatus> statuses = all(Status.AVAILABLE, Status.AVAILABLE);
        statuses.set(3, sf("MANUAL_REMOVAL_PLACENTA", "BASIC", Status.UNKNOWN));

        assertThat(EmoncReadinessService.classify(statuses))
                .isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
    }

    /** Stale evidence is not current evidence — it cannot sustain a CEmONC label. */
    @Test
    void staleEvidenceCannotSustainAClassification() {
        List<SignalFunctionStatus> statuses = all(Status.AVAILABLE, Status.AVAILABLE);
        statuses.set(8, sf("BLOOD_TRANSFUSION", "COMPREHENSIVE", Status.STALE));

        assertThat(EmoncReadinessService.classify(statuses))
                .isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
    }

    // ── the positive cases, which must still be reachable ───────────────────

    @Test
    void allNineConfirmedIsCemonc() {
        assertThat(EmoncReadinessService.classify(all(Status.AVAILABLE, Status.AVAILABLE)))
                .isEqualTo(Classification.CEMONC);
    }

    /** BEmONC requires the comprehensive pair to be positively assessed as absent, not merely unknown. */
    @Test
    void bemoncRequiresTheComprehensivePairToBeMeasuredNotMissing() {
        assertThat(EmoncReadinessService.classify(all(Status.AVAILABLE, Status.NOT_AVAILABLE)))
                .isEqualTo(Classification.BEMONC);
        assertThat(EmoncReadinessService.classify(all(Status.AVAILABLE, Status.UNKNOWN)))
                .as("unknown comprehensive functions must not be read as a BEmONC ceiling")
                .isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
    }

    /** A fully assessed facility that performs none of them is a real negative — that is allowed. */
    @Test
    void aFullyAssessedFacilityWithNoFunctionsIsNeither() {
        assertThat(EmoncReadinessService.classify(all(Status.NOT_AVAILABLE, Status.NOT_AVAILABLE)))
                .isEqualTo(Classification.NEITHER);
    }

    @Test
    void noSignalFunctionsAtAllIsInsufficientEvidence() {
        assertThat(EmoncReadinessService.classify(List.of()))
                .isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
    }

    // ── decay ───────────────────────────────────────────────────────────────

    @Test
    void aPerformanceWithinTheRecallWindowIsAvailable() {
        Instant recent = Instant.now().minus(10, ChronoUnit.DAYS);
        assertThat(EmoncReadinessService.resolveStatus("PERFORMED", recent, 3))
                .isEqualTo(Status.AVAILABLE);
    }

    /** Past the window the facility has not been shown to have stopped — only to be unobserved. */
    @Test
    void anAgedPerformanceBecomesStaleNotUnavailable() {
        Instant old = Instant.now().minus(200, ChronoUnit.DAYS);
        Status status = EmoncReadinessService.resolveStatus("PERFORMED", old, 3);
        assertThat(status).isEqualTo(Status.STALE);
        assertThat(status).isNotEqualTo(Status.NOT_AVAILABLE);
    }

    /** A negative finding does not decay into ignorance; it was measured. */
    @Test
    void aNegativeFindingStaysNegative() {
        assertThat(EmoncReadinessService.resolveStatus("NOT_PERFORMED",
                Instant.now().minus(400, ChronoUnit.DAYS), 3)).isEqualTo(Status.NOT_AVAILABLE);
    }

    @Test
    void anUnobservedFunctionIsUnknown() {
        assertThat(EmoncReadinessService.resolveStatus(null, null, 3)).isEqualTo(Status.UNKNOWN);
        assertThat(EmoncReadinessService.resolveStatus("NOT_ASSESSED", Instant.now(), 3))
                .isEqualTo(Status.UNKNOWN);
    }

    /** The recall window travels with the assessment; a longer survey window must be honoured. */
    @Test
    void theRecallWindowIsTakenFromTheAssessment() {
        Instant fourMonthsAgo = Instant.now().minus(120, ChronoUnit.DAYS);
        assertThat(EmoncReadinessService.resolveStatus("PERFORMED", fourMonthsAgo, 3))
                .isEqualTo(Status.STALE);
        assertThat(EmoncReadinessService.resolveStatus("PERFORMED", fourMonthsAgo, 12))
                .isEqualTo(Status.AVAILABLE);
    }

    // ── what the clinician is told ──────────────────────────────────────────

    /** The never-assessed narrative must say explicitly that this is not a negative finding. */
    @Test
    void theNeverAssessedNarrativeRefusesToImplyIncapability() {
        String narrative = EmoncReadinessService.narrate(
                Classification.INSUFFICIENT_EVIDENCE, BASIC, List.of(), List.of(), null);
        assertThat(narrative)
                .contains("never had an EmONC readiness assessment")
                .contains("not a statement that it has none");
    }

    /** Even a full CEmONC result must not imply the facility is open and staffed right now. */
    @Test
    void evenCemoncTellsTheClinicianToCallAhead() {
        assertThat(EmoncReadinessService.narrate(
                Classification.CEMONC, List.of(), List.of(), List.of(), Instant.now()))
                .contains("does not confirm the facility is open")
                .contains("call ahead");
    }

    @Test
    void everyNarrativeEndsWithCallAhead() {
        for (Classification c : Classification.values()) {
            String narrative = EmoncReadinessService.narrate(
                    c, List.of("X"), List.of("Y"), List.of("Z"), Instant.now());
            assertThat(narrative).as("narrative for %s", c).containsIgnoringCase("call ahead");
        }
    }
}
