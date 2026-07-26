package zw.gov.mohcc.impilo.clinical.imam;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The build gate for the IMAM pack. Every fixture in the content file is executed against the live
 * engine, so a content edit that changes clinical behaviour fails the build rather than reaching a
 * child. The properties below the fixtures are the ones the pack exists to guarantee.
 */
class ImamProgrammeServiceTest {

    private ImamProgrammeService service;

    @BeforeEach
    void setUp() {
        service = new ImamProgrammeService(new ObjectMapper());
    }

    private static Map<String, Object> visit(String date, double muac, String oedema, double weightKg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("visitDate", date);
        m.put("attended", true);
        m.put("muacCm", muac);
        m.put("oedema", oedema);
        m.put("weightKg", weightKg);
        m.put("dangerSignsPresent", false);
        return m;
    }

    // --- the content's own fixtures, run against the engine ------------------------------------

    @TestFactory
    @DisplayName("IMAM content fixtures")
    Stream<DynamicTest> contentFixturesPass() {
        ImamProgrammeContent content = service.content();
        assertThat(content.programmes()).as("IMAM pack must load").isNotEmpty();
        assertThat(content.testCases()).as("pack must ship fixtures").isNotEmpty();

        List<DynamicTest> tests = new ArrayList<>();
        for (ImamProgrammeContent.TestCase f : content.testCases()) {
            tests.add(DynamicTest.dynamicTest(f.kind() + ": " + f.name(), () -> {
                if ("ELIGIBILITY".equals(f.kind())) {
                    var e = service.eligibility(f.facts());
                    if (f.expectAssessable() != null) {
                        assertThat(e.assessable()).as("%s — assessable", f.name()).isEqualTo(f.expectAssessable());
                    }
                    if (f.expectEligible() != null) {
                        assertThat(e.eligible()).as("%s — eligible", f.name()).isEqualTo(f.expectEligible());
                    }
                    if (f.expectProgramme() != null) {
                        assertThat(e.programme()).as("%s — programme", f.name()).isEqualTo(f.expectProgramme());
                    }
                    if (f.expectAdmissionCriterion() != null) {
                        assertThat(e.admissionCriterion()).as("%s — criterion", f.name())
                                .isEqualTo(f.expectAdmissionCriterion());
                    }
                    return;
                }

                var p = service.progress(f.programme(), LocalDate.parse(f.admittedOn()), f.admissionOedema(),
                        f.visits(), LocalDate.parse(f.asOf()));
                if (f.expectDischargeEligible() != null) {
                    assertThat(p.dischargeEligible()).as("%s — discharge eligible", f.name())
                            .isEqualTo(f.expectDischargeEligible());
                }
                if (f.expectUnmetCriteria() != null && !f.expectUnmetCriteria().isEmpty()) {
                    assertThat(p.unmetCriteria()).as("%s — unmet criteria", f.name())
                            .containsExactlyInAnyOrderElementsOf(f.expectUnmetCriteria());
                }
                if (f.expectAttendanceStatus() != null) {
                    assertThat(p.attendanceStatus()).as("%s — attendance", f.name())
                            .isEqualTo(f.expectAttendanceStatus());
                }
                if (f.expectResponseStatus() != null) {
                    assertThat(p.responseStatus()).as("%s — response", f.name())
                            .isEqualTo(f.expectResponseStatus());
                }
                if (f.expectEscalation() != null) {
                    assertThat(p.escalation()).as("%s — escalation", f.name()).isEqualTo(f.expectEscalation());
                }
                if (f.expectNoReviewRecorded() != null) {
                    assertThat(p.noReviewRecorded()).as("%s — no review recorded", f.name())
                            .isEqualTo(f.expectNoReviewRecorded());
                }
            }));
        }
        return tests.stream();
    }

    // --- the rule the whole pack exists to enforce -----------------------------------------------

    @Test
    void oneGoodArmCircumferenceDoesNotDischargeAChild() {
        // The single most consequential line in this pack. A tape held a centimetre high, a
        // different measurer, or a child who tensed their arm all produce one good reading, and
        // discharging on it stops treatment for a child who is still wasted.
        var p = service.progress("OTP", LocalDate.parse("2026-06-01"), "ABSENT",
                List.of(visit("2026-06-22", 12.9, "ABSENT", 8.0)), LocalDate.parse("2026-06-23"));

        assertThat(p.dischargeEligible()).isFalse();
        assertThat(p.unmetCriteria()).contains("MUAC_SUSTAINED");
        assertThat(detail(p, "MUAC_SUSTAINED")).contains("2 consecutive are required");
    }

    @Test
    void twoConsecutiveGoodMeasurementsDischargeAsCured() {
        var p = service.progress("OTP", LocalDate.parse("2026-06-01"), "ABSENT",
                List.of(visit("2026-06-15", 12.6, "ABSENT", 7.8), visit("2026-06-22", 12.9, "ABSENT", 8.0)),
                LocalDate.parse("2026-06-23"));

        assertThat(p.dischargeEligible()).isTrue();
        assertThat(p.dischargeOutcome()).isEqualTo("CURED");
    }

    @Test
    void aGoodMeasurementFollowedByABadOneIsNotSustained() {
        // Consecutiveness is evaluated on the most recent reviews, not on "any two in the episode".
        var p = service.progress("OTP", LocalDate.parse("2026-06-01"), "ABSENT",
                List.of(visit("2026-06-15", 12.9, "ABSENT", 8.0), visit("2026-06-22", 12.1, "ABSENT", 7.9)),
                LocalDate.parse("2026-06-23"));

        assertThat(p.dischargeEligible()).isFalse();
        assertThat(p.unmetCriteria()).contains("MUAC_SUSTAINED");
    }

    // --- silence is never reassurance --------------------------------------------------------------

    @Test
    void anEpisodeWithNoRecordedReviewIsNeverReportedAsAChildDoingWell() {
        var p = service.progress("OTP", LocalDate.parse("2026-06-01"), "ABSENT",
                List.of(), LocalDate.parse("2026-06-23"));

        assertThat(p.noReviewRecorded()).isTrue();
        assertThat(p.reviewsRecorded()).isZero();
        assertThat(p.dischargeEligible()).isFalse();
        assertThat(p.responseStatus()).isNotEqualTo("RESPONDING");
        assertThat(p.attendanceStatus()).isEqualTo("DEFAULTER");
        assertThat(String.join(" ", p.actions()))
                .contains("An episode with no recorded review is not a child doing well");
    }

    @Test
    void oedemaThatWasNeverAssessedCannotCertifyAnOedemaFreeDischarge() {
        Map<String, Object> a = visit("2026-06-15", 12.9, null, 8.0);
        Map<String, Object> b = visit("2026-06-22", 13.0, null, 8.2);

        var p = service.progress("OTP", LocalDate.parse("2026-05-01"), "NOT_ASSESSED",
                List.of(a, b), LocalDate.parse("2026-06-23"));

        assertThat(p.dischargeEligible()).isFalse();
        assertThat(p.unmetCriteria()).containsExactly("OEDEMA_FREE");
        assertThat(detail(p, "OEDEMA_FREE")).contains("not an absent finding");
    }

    @Test
    void dangerSignsNotAssessedAtTheLastReviewBlockDischargeRatherThanPassingIt() {
        Map<String, Object> a = visit("2026-06-15", 12.9, "ABSENT", 8.0);
        Map<String, Object> b = visit("2026-06-22", 13.0, "ABSENT", 8.2);
        b.put("dangerSignsPresent", null);

        var p = service.progress("OTP", LocalDate.parse("2026-05-01"), "ABSENT",
                List.of(a, b), LocalDate.parse("2026-06-23"));

        assertThat(p.unmetCriteria()).contains("NO_ACUTE_MEDICAL_PROBLEM");
    }

    // --- defaulting and tracing ---------------------------------------------------------------------

    @Test
    void tracingIsTriggeredBeforeTheChildMeetsTheDefaulterDefinition() {
        // The whole point of separating the two thresholds: by the time a child is a defaulter, the
        // window in which a home visit brings them back has usually closed.
        var oneMissed = service.progress("OTP", LocalDate.parse("2026-06-01"), "ABSENT",
                List.of(visit("2026-06-08", 11.8, "ABSENT", 7.2)), LocalDate.parse("2026-06-19"));

        assertThat(oneMissed.attendanceStatus()).isEqualTo("TRACE_REQUIRED");
        assertThat(oneMissed.tracingRequired()).isTrue();
        assertThat(oneMissed.consecutiveMissedVisits()).isEqualTo(1);

        var twoMissed = service.progress("OTP", LocalDate.parse("2026-06-01"), "ABSENT",
                List.of(visit("2026-06-08", 11.8, "ABSENT", 7.2)), LocalDate.parse("2026-06-26"));

        assertThat(twoMissed.attendanceStatus()).isEqualTo("DEFAULTER");
        assertThat(twoMissed.consecutiveMissedVisits()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void aChildADayLateHasNotMissedAVisit() {
        // Without the grace window every programme reports its entire caseload as absent, and a
        // tracing list that names everybody names nobody.
        var p = service.progress("OTP", LocalDate.parse("2026-06-01"), "ABSENT",
                List.of(visit("2026-06-08", 11.8, "ABSENT", 7.2)), LocalDate.parse("2026-06-16"));

        assertThat(p.attendanceStatus()).isEqualTo("ATTENDING");
        assertThat(p.consecutiveMissedVisits()).isZero();
    }

    @Test
    void aMissedVisitIsCountedFromElapsedTimeEvenWhenNobodyRecordedTheMiss() {
        // The commonest way a defaulter goes unnoticed is that no row was ever created for the visit
        // they did not attend. Counting rows alone would report this child as attending.
        var p = service.progress("SFP", LocalDate.parse("2026-04-01"), "ABSENT",
                List.of(visit("2026-04-15", 12.0, "ABSENT", 8.0)), LocalDate.parse("2026-05-20"));

        assertThat(p.attendanceStatus()).isEqualTo("DEFAULTER");
    }

    // --- escalation out of the wrong programme ---------------------------------------------------------

    @Test
    void oedemaAppearingInSupplementaryFeedingEscalatesToTherapeuticCare() {
        var p = service.progress("SFP", LocalDate.parse("2026-05-01"), "ABSENT",
                List.of(visit("2026-05-15", 12.0, "ABSENT", 8.0), visit("2026-05-29", 12.6, "PRESENT", 8.4)),
                LocalDate.parse("2026-06-01"));

        assertThat(p.escalation()).isEqualTo("OEDEMA_IN_SFP");
        assertThat(p.escalationAction()).contains("severe acute malnutrition");
        // And it must not discharge on the good arm circumference while oedema is present.
        assertThat(p.dischargeEligible()).isFalse();
    }

    // --- admission routing ------------------------------------------------------------------------------

    @Test
    void anImnciClassificationRoutesStraightIntoAProgramme() {
        // This is the wire that was missing: the classification engine said "enrol in outpatient
        // therapeutic care" and there was nowhere to enrol.
        var e = service.eligibility(Map.of("ageDays", 400,
                "classification", "UNCOMPLICATED_SEVERE_ACUTE_MALNUTRITION"));

        assertThat(e.eligible()).isTrue();
        assertThat(e.programme()).isEqualTo("OTP");
        assertThat(e.reviewIntervalDays()).isEqualTo(7);
        assertThat(e.therapeuticFood()).isEqualTo("RUTF");
    }

    @Test
    void anUnmeasuredChildIsNotAssessableRatherThanNotEligible() {
        var e = service.eligibility(Map.of("ageDays", 400));

        assertThat(e.assessable()).isFalse();
        assertThat(e.eligible()).isFalse();
        assertThat(e.notAssessableReason()).contains("unmeasured child");
        assertThat(e.missingInputs()).contains("muacCm", "bilateralPittingOedema");
    }

    // --- ration ------------------------------------------------------------------------------------------

    @Test
    void aChildWhoHasStoppedAttendingIsNotGivenARationInstructionInsteadOfATrace() {
        // Caught by reading a live worklist: "Issue 21 sachets of RUTF" appeared on the row of a
        // defaulting child. It reads as a task to complete, and the task that matters for a child
        // who has stopped coming is finding them.
        var p = service.progress("OTP", LocalDate.parse("2026-06-01"), "ABSENT",
                List.of(visit("2026-06-08", 11.0, "ABSENT", 8.0)), LocalDate.parse("2026-06-26"));

        assertThat(p.attendanceStatus()).isEqualTo("DEFAULTER");
        assertThat(String.join(" ", p.actions())).doesNotContain("Issue ");
        assertThat(String.join(" ", p.actions())).contains("defaulter definition");
        // The ration is still reported as a value — a caller may need it — it is simply not an
        // instruction for today.
        assertThat(p.recommendedSachetsPerWeek()).isEqualByComparingTo("21");
    }

    @Test
    void theWeeklyRationIsDerivedFromTheChildsCurrentWeight() {
        var p = service.progress("OTP", LocalDate.parse("2026-06-01"), "ABSENT",
                List.of(visit("2026-06-08", 11.0, "ABSENT", 8.0)), LocalDate.parse("2026-06-09"));

        // 8.0 kg falls in the 7.0–9.9 kg band: 21 sachets a week.
        assertThat(p.recommendedSachetsPerWeek()).isEqualByComparingTo("21");
        assertThat(p.therapeuticFood()).isEqualTo("RUTF");
    }

    // --- provenance ---------------------------------------------------------------------------------------

    @Test
    void everyProgrammeDeclaresDischargeCriteriaAndADefaulterDefinition() {
        for (ImamProgrammeContent.Programme programme : service.content().programmes()) {
            assertThat(programme.dischargeCriteria()).as("%s discharge criteria", programme.code()).isNotNull();
            assertThat(programme.dischargeCriteria().narrative()).as("%s narrative", programme.code()).isNotBlank();
            assertThat(programme.defaulterMissedVisits()).as("%s defaulter", programme.code()).isPositive();
            assertThat(programme.reviewIntervalDays()).as("%s review interval", programme.code()).isPositive();
            assertThat(programme.traceAfterMissedVisits())
                    .as("%s must trace no later than it defaults", programme.code())
                    .isLessThanOrEqualTo(programme.defaulterMissedVisits());
        }
    }

    @Test
    void theSustainedClauseIsNeverWeakenedToASingleVisit() {
        // A guard against the edit that would silently undo the pack's central safety property.
        for (ImamProgrammeContent.Programme programme : service.content().programmes()) {
            if (programme.dischargeCriteria().muacAtLeastCm() != null) {
                assertThat(programme.dischargeCriteria().sustainedConsecutiveVisits())
                        .as("%s must require more than one measurement to discharge", programme.code())
                        .isGreaterThanOrEqualTo(2);
            }
        }
    }

    @Test
    void thePackDoesNotClaimRatificationItDoesNotHave() {
        var content = service.content();
        assertThat(content.approvalStatus()).isEqualTo("ENGINEERING_SEED");
        assertThat(content.adaptationAuthority()).isEqualTo("PENDING_MOHCC_RATIFICATION");
        assertThat(content.provenance()).contains("WHO/UNICEF");
        assertThat(content.version()).isEqualTo("engineering-seed-1.0.0");
    }

    @Test
    void everyAssessmentCarriesItsContentVersionAndApprovalStatus() {
        var p = service.progress("OTP", LocalDate.parse("2026-06-01"), "ABSENT", List.of(),
                LocalDate.parse("2026-06-02"));

        assertThat(p.contentVersion()).isEqualTo("engineering-seed-1.0.0");
        assertThat(p.approvalStatus()).isEqualTo("ENGINEERING_SEED");
        assertThat(p.note()).contains("national IMAM protocol");
    }

    @Test
    void leavingInpatientStabilisationIsATransferAndNotACure() {
        // Recording it as a cure would close the episode of a child who is still severely
        // malnourished and merely well enough to be treated at home.
        Map<String, Object> v = visit("2026-06-05", 10.9, "ABSENT", 6.0);
        v.put("appetiteTest", "PASSED");

        var p = service.progress("INPATIENT_STABILISATION", LocalDate.parse("2026-06-01"), "PRESENT",
                List.of(v), LocalDate.parse("2026-06-06"));

        assertThat(p.dischargeEligible()).isTrue();
        assertThat(p.dischargeOutcome()).isEqualTo("TRANSFERRED_OUT");
        assertThat(p.dischargeDestination()).isEqualTo("OTP");
        assertThat(String.join(" ", p.actions())).contains("not a cure");
    }

    private static String detail(ImamProgrammeEngine.Progress p, String code) {
        return p.dischargeCriteria().stream().filter(c -> c.code().equals(code))
                .map(ImamProgrammeEngine.Criterion::detail).findFirst().orElse("");
    }
}
