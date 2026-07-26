package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assessment programme guard tests.
 *
 * <p>The property under test is that the programme cannot flatter itself. Two ways it could:
 * by reporting coverage as though it were currency, and by letting a round complete without
 * anything having been observed. Both are closed here and in the schema.</p>
 */
class ReadinessProgrammeTest {

    private static String migration() throws Exception {
        return Files.readString(Path.of(
                "src/main/resources/db/migration/V043__readiness_assessment_programme.sql"));
    }

    // ── the number that matters ─────────────────────────────────────────────

    /**
     * The whole reason the programme reports two numbers: a network assessed once a year has 100%
     * coverage and a register that is right for one quarter in four.
     */
    @Test
    void theGapBetweenCoverageAndCurrencyIsStatedOutLoud() {
        String narrative = ReadinessAssessmentProgrammeService.narrate(
                904, 904, 200, 704, 0, 22.1, 100.0);
        assertThat(narrative)
                .contains("Coverage")
                .contains("ahead of currency")
                .contains("aging faster than it is being refreshed");
    }

    /** Expired observations must read as unobserved, never as decline. */
    @Test
    void expiredObservationsAreNotReportedAsDecline() {
        String narrative = ReadinessAssessmentProgrammeService.narrate(
                904, 500, 100, 400, 404, 11.1, 55.3);
        assertThat(narrative)
                .contains("expired")
                .contains("unobserved again, not shown to have declined");
    }

    /** The zero state is the current one, and it must not read as "no capability". */
    @Test
    void theUnassessedNetworkIsDescribedAsUnmeasuredNotAbsent() {
        String narrative = ReadinessAssessmentProgrammeService.narrate(
                904, 0, 0, 0, 904, 0.0, 0.0);
        assertThat(narrative)
                .contains("has ever had a readiness assessment")
                .contains("unmeasured")
                .contains("not the same as absent");
    }

    @Test
    void anEmptyRegisterSaysSoRatherThanReportingZeroPercent() {
        assertThat(ReadinessAssessmentProgrammeService.narrate(0, 0, 0, 0, 0, 0.0, 0.0))
                .contains("nothing to assess");
    }

    // ── the cadence constraint ──────────────────────────────────────────────

    /**
     * A round longer than its own recall window cannot produce a current register — the facilities
     * assessed first have expired before the last are visited. Refused at the schema, because it is
     * the failure that makes an assessment programme look productive while producing nothing usable.
     */
    @Test
    void theSchemaRefusesARoundLongerThanItsRecallWindow() throws Exception {
        assertThat(migration()).contains("chk_round_not_longer_than_recall");
        assertThat(migration()).contains("closes_on - opens_on <= recall_window_months * 31");
    }

    /** Facilities become due before their data expires, not after. */
    @Test
    void facilitiesFallDueAheadOfExpiry() {
        assertThat(ReadinessAssessmentProgrammeService.DUE_LEAD_DAYS).isGreaterThan(0);
    }

    // ── an assignment is not an observation ─────────────────────────────────

    /** COMPLETED requires a real assessment; a round cannot report itself done having recorded nothing. */
    @Test
    void completionRequiresARecordedAssessment() throws Exception {
        assertThat(migration())
                .contains("chk_completed_requires_assessment")
                .contains("status <> 'COMPLETED' OR assessment_id IS NOT NULL");
    }

    /**
     * Closing a round must not resolve the facilities nobody reached. Running out of time is not a
     * finding about a facility, and a 40%-complete round leaves 60% UNKNOWN, not 60% unequipped.
     */
    @Test
    void closingARoundDoesNotResolveOutstandingAssignments() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/zw/gov/mohcc/impilo/tuso/core/ReadinessAssessmentProgrammeService.java"));
        int closeStart = source.indexOf("public Map<String, Object> closeRound");
        int closeEnd = source.indexOf("public void assign", closeStart);
        String closeBody = source.substring(closeStart, closeEnd);

        assertThat(closeBody)
                .as("closeRound must not write assignment statuses")
                .doesNotContain("UPDATE tuso.readiness_assessment_assignment");
        assertThat(closeBody).contains("readiness_assessment_round");
    }

    /** UNREACHABLE must stay distinguishable from "nobody got round to it". */
    @Test
    void unreachableIsItsOwnOutcome() throws Exception {
        assertThat(migration()).contains("'UNREACHABLE'");
        assertThat(migration()).contains("'PENDING', 'IN_PROGRESS', 'COMPLETED', 'UNREACHABLE', 'DECLINED'");
    }

    /** No programme-level readiness flag on the facility — one answer, derived from observations. */
    @Test
    void theProgrammeDoesNotStoreASecondReadinessAnswer() throws Exception {
        assertThat(migration())
                .as("a flag on tuso.facility would become a staler second answer")
                .doesNotContain("ALTER TABLE tuso.facility ");
    }
}
