package zw.gov.mohcc.impilo.clinical.contraception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contraceptive initiation: the questions asked after eligibility has been settled.
 *
 * <p>These tests are written around one failure mode. Two complementary rules — "no backup needed"
 * and "backup needed for seven days" — both evaluate to unknown when the cycle day is missing, so
 * neither fires, and a guidance panel that renders nothing is read as a panel saying nothing is
 * needed. The cost of that is a woman told she is protected who is not.
 */
class ContraceptiveInitiationServiceTest {

    private final ContraceptiveInitiationService service =
            new ContraceptiveInitiationService(new RuleContentLoader(new ObjectMapper()),
                    new ObjectMapper());

    private static Map<String, Object> facts(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static List<String> codes(ContraceptiveInitiationService.InitiationAssessment result) {
        return result.guidance().stream()
                .map(ContraceptiveInitiationService.Guidance::code).toList();
    }

    // ---------------------------------------------------------------- reasonable certainty

    @Test
    @DisplayName("one met criterion with no signs of pregnancy establishes reasonable certainty")
    void oneMetCriterionEstablishesCertainty() {
        var result = service.assess("COMBINED_ORAL_PILL", facts(
                "pregnancySignsOrSymptoms", "ABSENT",
                "daysSinceMensesOnset", 2));

        assertThat(result.pregnancyCertainty().verdict()).isEqualTo(PregnancyCertainty.ESTABLISHED);
        assertThat(result.pregnancyCertainty().criteriaMet())
                .contains("SPR_RC_03_WITHIN_7_DAYS_OF_MENSES");
    }

    @Test
    @DisplayName("a met criterion does not leave the answer provisional on questions that could not have changed it")
    void aMetCriterionEndsTheEnquiry() {
        var result = service.assess("COMBINED_ORAL_PILL", facts(
                "pregnancySignsOrSymptoms", "ABSENT",
                "daysSinceMensesOnset", 2));

        // Five other criteria are unanswered. None of them could turn ESTABLISHED into anything
        // else, and reporting them would flag every result provisional until a clinician learned to
        // ignore the flag.
        assertThat(result.pregnancyCertainty().criteriaUnanswered()).isEmpty();
        assertThat(result.pregnancyCertainty().missingInputs()).isEmpty();
    }

    @Test
    @DisplayName("signs of pregnancy settle it whatever the cycle timing says")
    void signsOfPregnancyOverrideTiming() {
        var result = service.assess("COMBINED_ORAL_PILL", facts(
                "pregnancySignsOrSymptoms", "PRESENT",
                "daysSinceMensesOnset", 2,
                "intercourseSinceLastMenses", "NO"));

        // Two criteria are met. The gate is not one of the alternatives, and it wins.
        assertThat(result.pregnancyCertainty().verdict())
                .isEqualTo(PregnancyCertainty.NOT_ESTABLISHED);
        assertThat(result.pregnancyCertainty().rationale()).contains("signs of pregnancy");
    }

    @Test
    @DisplayName("asking nothing is CANNOT_DETERMINE, not NOT_ESTABLISHED")
    void anUnaskedChecklistIsNotANegativeAnswer() {
        var result = service.assess("COMBINED_ORAL_PILL", facts());

        assertThat(result.pregnancyCertainty().verdict())
                .isEqualTo(PregnancyCertainty.CANNOT_DETERMINE);
    }

    @Test
    @DisplayName("every criterion asked and none met is NOT_ESTABLISHED, and names no missing question")
    void afullyAskedChecklistWithNoCriterionMetIsAnAnswer() {
        var result = service.assess("COMBINED_ORAL_PILL", facts(
                "pregnancySignsOrSymptoms", "ABSENT",
                "intercourseSinceLastMenses", "YES",
                "usingReliableMethodConsistently", "NO",
                "daysSinceMensesOnset", 20,
                "daysPostpartum", 400,
                "breastfeedingStatus", "PARTIAL",
                "amenorrhoeicSinceDelivery", "NO",
                "daysSincePregnancyEnd", 400));

        assertThat(result.pregnancyCertainty().verdict())
                .isEqualTo(PregnancyCertainty.NOT_ESTABLISHED);
        assertThat(result.pregnancyCertainty().criteriaUnanswered()).isEmpty();
        assertThat(result.pregnancyCertainty().missingInputs()).isEmpty();
    }

    @Test
    @DisplayName("a partly asked checklist names the questions that would settle it")
    void anIncompleteChecklistNamesWhatWouldSettleIt() {
        var result = service.assess("COMBINED_ORAL_PILL", facts(
                "pregnancySignsOrSymptoms", "ABSENT",
                "intercourseSinceLastMenses", "YES",
                "daysSinceMensesOnset", 20));

        assertThat(result.pregnancyCertainty().verdict())
                .isEqualTo(PregnancyCertainty.CANNOT_DETERMINE);
        assertThat(result.pregnancyCertainty().missingInputs())
                .contains("usingReliableMethodConsistently");
        assertThat(result.pregnancyCertainty().rationale()).contains("was not");
    }

    @Test
    @DisplayName("an unrecorded pregnancy-signs question blocks the checklist rather than passing the gate")
    void theGateIsNotAssumedWhenUnasked() {
        var result = service.assess("COMBINED_ORAL_PILL", facts("daysSinceMensesOnset", 2));

        // Criterion 3 is met. It does not matter: absence of a recorded answer about signs of
        // pregnancy is not the same as a recorded absence of signs.
        assertThat(result.pregnancyCertainty().verdict())
                .isEqualTo(PregnancyCertainty.CANNOT_DETERMINE);
        assertThat(result.pregnancyCertainty().criteriaUnanswered())
                .containsExactly("SPR_RC_NO_SIGNS_OF_PREGNANCY");
    }

    @Test
    @DisplayName("lactational amenorrhoea needs all three of its conditions, not one")
    void lamNeedsAllThreeConditions() {
        var breastfeedingButMenstruating = service.assess("COMBINED_ORAL_PILL", facts(
                "pregnancySignsOrSymptoms", "ABSENT",
                "breastfeedingStatus", "FULL",
                "amenorrhoeicSinceDelivery", "NO",
                "daysPostpartum", 90,
                "intercourseSinceLastMenses", "YES",
                "usingReliableMethodConsistently", "NO",
                "daysSinceMensesOnset", 20,
                "daysSincePregnancyEnd", 400));

        assertThat(breastfeedingButMenstruating.pregnancyCertainty().verdict())
                .isEqualTo(PregnancyCertainty.NOT_ESTABLISHED);
    }

    // ---------------------------------------------------------------- backup contraception

    @Test
    @DisplayName("a missing cycle day still produces backup advice rather than an empty panel")
    void anUnknownCycleDayStillProducesAdvice() {
        var result = service.assess("COMBINED_ORAL_PILL", facts(
                "pregnancySignsOrSymptoms", "ABSENT",
                "intercourseSinceLastMenses", "NO"));

        // Certainty is established by criterion 1, so nothing about the pregnancy question forces
        // guidance. The cycle day is still unknown, and that must not render as "no backup needed".
        assertThat(result.pregnancyCertainty().verdict()).isEqualTo(PregnancyCertainty.ESTABLISHED);
        assertThat(codes(result)).contains("SPR_BACKUP_UNKNOWN_COC");
        assertThat(codes(result)).doesNotContain("SPR_BACKUP_NONE_COC");
    }

    @Test
    @DisplayName("exactly one backup verdict is produced for a method, never two")
    void exactlyOneBackupVerdictPerMethod() {
        for (String method : List.of("COMBINED_ORAL_PILL", "PROGESTOGEN_ONLY_PILL",
                "INJECTABLE_DMPA_IM", "IMPLANT_ETONOGESTREL_1ROD", "IUS_LEVONORGESTREL",
                "IUD_COPPER_T380A")) {
            for (Integer day : new Integer[]{null, 0, 4, 5, 6, 7, 20}) {
                Map<String, Object> f = facts("pregnancySignsOrSymptoms", "ABSENT");
                if (day != null) {
                    f.put("daysSinceMensesOnset", day);
                }
                var backup = service.assess(method, f).guidance().stream()
                        .filter(g -> "BACKUP_CONTRACEPTION".equals(g.ruleType())).toList();

                // One and only one. Two would put contradictory advice on one screen; none would
                // put no advice on it, which reads as none being needed.
                assertThat(backup)
                        .as("method=%s cycleDay=%s", method, day)
                        .hasSize(1);
            }
        }
    }

    @Test
    @DisplayName("the copper device is the one method with no backup period at any point in the cycle")
    void copperIudNeedsNoBackupEver() {
        for (Integer day : new Integer[]{null, 0, 6, 22}) {
            Map<String, Object> f = facts("pregnancySignsOrSymptoms", "ABSENT");
            if (day != null) {
                f.put("daysSinceMensesOnset", day);
            }
            assertThat(codes(service.assess("IUD_COPPER_T380A", f)))
                    .as("cycleDay=%s", day)
                    .contains("SPR_BACKUP_NONE_COPPER_IUD");
        }
    }

    @Test
    @DisplayName("the progestogen-only pill gets its own 2-day period, not the combined 7")
    void backupPeriodsAreNotInterchangeable() {
        var pop = codes(service.assess("PROGESTOGEN_ONLY_PILL", facts(
                "pregnancySignsOrSymptoms", "ABSENT", "daysSinceMensesOnset", 10)));
        var coc = codes(service.assess("COMBINED_ORAL_PILL", facts(
                "pregnancySignsOrSymptoms", "ABSENT", "daysSinceMensesOnset", 10)));

        assertThat(pop).contains("SPR_BACKUP_2D_POP").doesNotContain("SPR_BACKUP_7D_COC");
        assertThat(coc).contains("SPR_BACKUP_7D_COC").doesNotContain("SPR_BACKUP_2D_POP");
    }

    // ---------------------------------------------------------------- access safeguards

    @Test
    @DisplayName("pregnancy that cannot be excluded raises a do-not-deny safeguard, not a refusal")
    void uncertainPregnancyDoesNotRefuseHerAMethod() {
        var result = service.assess("COMBINED_ORAL_PILL", facts());

        var safeguard = result.guidance().stream()
                .filter(g -> "SPR_PREGNANCY_UNCERTAIN_DO_NOT_DENY".equals(g.code()))
                .findFirst().orElseThrow();
        assertThat(safeguard.requiredAction()).contains("may still be started today");
        assertThat(safeguard.message()).contains("not a reason to send her away");
    }

    @Test
    @DisplayName("an intrauterine insertion is deferred when pregnancy is not excluded, and the deferral is a plan")
    void intrauterineInsertionIsDeferredNotDenied() {
        var result = service.assess("IUD_COPPER_T380A", facts());
        var codes = codes(result);

        // Both fire, and they say different things: do not send her away, and do not insert today.
        // The do-not-deny rule standing alone beside an intrauterine method reads as permission.
        assertThat(codes).contains("SPR_PREGNANCY_UNCERTAIN_DO_NOT_DENY",
                "SPR_LARC_DEFER_IF_PREGNANCY_UNCERTAIN");

        var defer = result.guidance().stream()
                .filter(g -> "SPR_LARC_DEFER_IF_PREGNANCY_UNCERTAIN".equals(g.code()))
                .findFirst().orElseThrow();
        assertThat(defer.requiredAction())
                .contains("bridge")
                .contains("return date");
    }

    @Test
    @DisplayName("an implant is not deferred — the deferral is about the uterus, not about long-acting methods")
    void theDeferralIsScopedToIntrauterineMethods() {
        assertThat(codes(service.assess("IMPLANT_ETONOGESTREL_1ROD", facts())))
                .doesNotContain("SPR_LARC_DEFER_IF_PREGNANCY_UNCERTAIN");
    }

    @Test
    @DisplayName("established certainty raises no safeguard at all")
    void establishedCertaintyRaisesNoSafeguard() {
        var codes = codes(service.assess("IUD_COPPER_T380A", facts(
                "pregnancySignsOrSymptoms", "ABSENT",
                "intercourseSinceLastMenses", "NO")));

        assertThat(codes).doesNotContain("SPR_PREGNANCY_UNCERTAIN_DO_NOT_DENY",
                "SPR_LARC_DEFER_IF_PREGNANCY_UNCERTAIN");
    }

    // ---------------------------------------------------------------- completeness reporting

    @Test
    @DisplayName("no method named means the assessment says so rather than looking complete")
    void noMethodMeansAnIncompleteAssessment() {
        var result = service.assess(null, facts(
                "pregnancySignsOrSymptoms", "ABSENT",
                "intercourseSinceLastMenses", "NO"));

        assertThat(result.complete()).isFalse();
        assertThat(result.note()).contains("Backup contraception has not been assessed");
        assertThat(result.guidance().stream()
                .filter(g -> "BACKUP_CONTRACEPTION".equals(g.ruleType()))).isEmpty();
    }

    @Test
    @DisplayName("a method the content does not cover is reported as a gap, never as no backup needed")
    void anUncoveredMethodIsReportedAsAGap() {
        var result = service.assess("FERTILITY_AWARENESS", facts(
                "pregnancySignsOrSymptoms", "ABSENT",
                "intercourseSinceLastMenses", "NO"));

        assertThat(result.guidance().stream()
                .filter(g -> "BACKUP_CONTRACEPTION".equals(g.ruleType()))).isEmpty();
        assertThat(result.contentProblems())
                .anyMatch(p -> p.contains("FERTILITY_AWARENESS")
                        && p.contains("requiring backup"));
        assertThat(result.complete()).isFalse();
    }

    @Test
    @DisplayName("missing content is reported as unavailable, not as nothing to do")
    void missingContentIsNeverSilence() {
        var result = service.assess("clinical/does-not-exist.json", "COMBINED_ORAL_PILL", facts());

        assertThat(result.complete()).isFalse();
        assertThat(result.contentProblems()).isNotEmpty();
        assertThat(result.note()).contains("not a statement that none is needed");
        assertThat(result.pregnancyCertainty().verdict())
                .isEqualTo(PregnancyCertainty.CANNOT_DETERMINE);
    }

    @Test
    @DisplayName("every piece of guidance carries an action and a citation")
    void everyGuidanceIsActionableAndAttributable() {
        var result = service.assess("COMBINED_ORAL_PILL", facts());

        assertThat(result.guidance()).isNotEmpty();
        for (var guidance : result.guidance()) {
            assertThat(guidance.requiredAction())
                    .as("%s has no action", guidance.code()).isNotBlank();
            assertThat(guidance.sourceRefs())
                    .as("%s cites nothing", guidance.code()).isNotEmpty();
            assertThat(guidance.provenance())
                    .as("%s carries no provenance", guidance.code())
                    .containsKey("adaptation_type");
        }
    }

    @Test
    @DisplayName("the pack ships unratified and says so")
    void contentShipsAsAnEngineeringSeed() {
        var result = service.assess("COMBINED_ORAL_PILL", facts());

        assertThat(result.approvalStatus()).isEqualTo("ENGINEERING_SEED");
        assertThat(result.contentVersion()).isNotBlank();
    }
}
