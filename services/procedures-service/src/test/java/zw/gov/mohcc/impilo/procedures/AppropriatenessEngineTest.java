package zw.gov.mohcc.impilo.procedures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.procedures.api.dto.AppropriatenessDtos.AppropriatenessRequest;
import zw.gov.mohcc.impilo.procedures.api.dto.AppropriatenessDtos.AppropriatenessVerdict;
import zw.gov.mohcc.impilo.procedures.core.AppropriatenessEngine;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** §5 detections, and the rule none of them may break: never silently cancel. */
@SpringBootTest
@ActiveProfiles("test")
class AppropriatenessEngineTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired private AppropriatenessEngine engine;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM procedures.procedure_requirement");
        jdbc.update("DELETE FROM procedures.procedure_definition");
    }

    private void define(String code, String laterality, Integer lookback, String pregnancy,
                        Integer ageMin, Integer ageMax) {
        jdbc.update("""
                INSERT INTO procedures.procedure_definition
                  (id, tenant_id, definition_code, version, clinical_name, category, owning_specialty,
                   purpose, laterality_applicability, pregnancy_applicability, recovery_required,
                   status, approving_authority, duplicate_lookback_days, age_min_days, age_max_days)
                VALUES (?,?,?,1,?, 'THEATRE','SURGERY','THERAPEUTIC',?,?,false,
                        'PUBLISHED','PENDING_MOHCC_RATIFICATION',?,?,?)
                """, UUID.randomUUID(), TENANT, code, "Name of " + code, laterality, pregnancy,
                lookback, ageMin, ageMax);
    }

    private AppropriatenessRequest req(String code, String side) {
        return new AppropriatenessRequest(code, null, null, side, null, null, null, null,
                true, null, null, null, null, null, null, null);
    }

    @Test
    void aLateralisedProcedureWithNoSideIsBlockedNotWarned() {
        define("PROC-LAT", "LATERALISED", null, "NO_CONSTRAINT", null, null);

        AppropriatenessVerdict v = engine.evaluate(TENANT, req("PROC-LAT", null));

        assertThat(v.outcome()).isEqualTo("BLOCKED");
        assertThat(v.detections()).extracting("code").contains("SIDE_NOT_SPECIFIED");
        // A warning is something you click past. These procedures are done at the bedside by a
        // lone clinician with no second checker, so the disposition has to be BLOCK.
        assertThat(v.detections()).filteredOn(d -> d.code().equals("SIDE_NOT_SPECIFIED"))
                .allMatch(d -> d.disposition().equals("BLOCK"));
    }

    @Test
    void aLateralisedProcedureWithASideIsAppropriate() {
        define("PROC-LAT", "LATERALISED", null, "NO_CONSTRAINT", null, null);
        assertThat(engine.evaluate(TENANT, req("PROC-LAT", "LEFT")).outcome()).isEqualTo("APPROPRIATE");
    }

    @Test
    void aSideOnAMidlineProcedureIsQueriedRatherThanIgnored() {
        define("PROC-MID", "MIDLINE", null, "NO_CONSTRAINT", null, null);

        AppropriatenessVerdict v = engine.evaluate(TENANT, req("PROC-MID", "LEFT"));

        // One of the request and the catalogue is wrong; finding out which is the point.
        assertThat(v.detections()).extracting("code").contains("SIDE_ON_NON_LATERALISED_PROCEDURE");
        assertThat(v.outcome()).isEqualTo("PROCEED_WITH_CLARIFICATION");
    }

    /**
     * §27's own gap, closed here: {@code DUPLICATE_OPEN_REQUEST} has been real code since P2 but
     * had zero test coverage anywhere in the estate (confirmed by grep, Wave P15) — the flag is
     * set by the CALLER (OROS), not computed by this engine, so this proves the engine reacts to
     * it correctly rather than proving OROS's own open-request lookup.
     */
    @Test
    void anOpenRequestAssertedByTheCallerIsFlaggedAsADuplicate() {
        define("PROC-OPENDUP", "NOT_APPLICABLE", null, "NO_CONSTRAINT", null, null);
        AppropriatenessRequest r = new AppropriatenessRequest("PROC-OPENDUP", null, null, null,
                LocalDate.now(), null, null, null, null, true, null,
                null, null, null, null, null);

        AppropriatenessVerdict v = engine.evaluate(TENANT, r);

        assertThat(v.detections()).extracting("code").contains("DUPLICATE_OPEN_REQUEST");
        assertThat(v.outcome()).isEqualTo("PROCEED_WITH_CLARIFICATION");
    }

    /** The absence of the flag must not be read as an open request — null is "not asserted". */
    @Test
    void noOpenRequestAssertedMeansNoDuplicateDetection() {
        define("PROC-NODUP", "NOT_APPLICABLE", null, "NO_CONSTRAINT", null, null);
        AppropriatenessRequest r = new AppropriatenessRequest("PROC-NODUP", null, null, null,
                LocalDate.now(), null, null, null, null, false, null,
                null, null, null, null, null);

        AppropriatenessVerdict v = engine.evaluate(TENANT, r);

        assertThat(v.detections()).extracting("code").doesNotContain("DUPLICATE_OPEN_REQUEST");
    }

    @Test
    void anUndeclaredRepeatWindowIsReportedAsUnknownRatherThanAssumed() {
        define("PROC-NOWINDOW", "MIDLINE", null, "NO_CONSTRAINT", null, null);
        AppropriatenessRequest r = new AppropriatenessRequest("PROC-NOWINDOW", null, null, null,
                LocalDate.now(), null, null, null, true, null, LocalDate.now().minusDays(2),
                null, null, null, null, null);

        AppropriatenessVerdict v = engine.evaluate(TENANT, r);

        // A procedure with no declared window is not one that can never be a duplicate — it is
        // one nobody has decided about, and saying so beats a confident silence.
        assertThat(v.detections()).extracting("code").contains("DUPLICATE_WINDOW_UNDECLARED");
    }

    @Test
    void aRepeatInsideTheDeclaredWindowIsFlaggedAndOutsideItIsNot() {
        define("PROC-SCOPE", "NOT_APPLICABLE", 90, "NO_CONSTRAINT", null, null);

        AppropriatenessRequest inside = new AppropriatenessRequest("PROC-SCOPE", null, null, null,
                LocalDate.now(), null, null, null, true, null, LocalDate.now().minusDays(10),
                null, null, null, null, null);
        assertThat(engine.evaluate(TENANT, inside).detections())
                .extracting("code").contains("RECENT_EQUIVALENT_PROCEDURE");

        AppropriatenessRequest outside = new AppropriatenessRequest("PROC-SCOPE", null, null, null,
                LocalDate.now(), null, null, null, true, null, LocalDate.now().minusDays(200),
                null, null, null, null, null);
        assertThat(engine.evaluate(TENANT, outside).outcome()).isEqualTo("APPROPRIATE");
    }

    @Test
    void unknownPregnancyStatusIsDetectedWhereTheProcedureRequiresATest() {
        define("PROC-PREG", "MIDLINE", null, "REQUIRES_TEST", null, null);
        assertThat(engine.evaluate(TENANT, req("PROC-PREG", null)).detections())
                .extracting("code").contains("PREGNANCY_STATUS_UNKNOWN");
    }

    @Test
    void anAgeBoundedProcedureProposesAnAlternativeRatherThanRefusing() {
        define("PROC-PAED", "MIDLINE", null, "NO_CONSTRAINT", 0, 365);
        AppropriatenessRequest adult = new AppropriatenessRequest("PROC-PAED", null, null, null,
                null, 20000, null, null, true, null, null, null, null, null, null, null);

        AppropriatenessVerdict v = engine.evaluate(TENANT, adult);

        assertThat(v.detections()).extracting("code").contains("AGE_ABOVE_RANGE");
        assertThat(v.detections()).filteredOn(d -> d.code().equals("AGE_ABOVE_RANGE"))
                .allMatch(d -> d.disposition().equals("PROPOSE_ALTERNATIVE"));
    }

    @Test
    void anUnknownProcedureBlocksWithAnActionRatherThanFailing() {
        AppropriatenessVerdict v = engine.evaluate(TENANT, req("PROC-DOES-NOT-EXIST", null));

        assertThat(v.outcome()).isEqualTo("BLOCKED");
        assertThat(v.detections()).extracting("code").containsExactly("UNKNOWN_PROCEDURE");
        assertThat(v.detections().get(0).suggestedAction()).isNotBlank();
    }

    /** The governing rule of §5, asserted directly rather than assumed from the code's shape. */
    @Test
    void noDetectionEverCancelsAndEveryOneSaysWhatToDoNext() {
        define("PROC-LAT", "LATERALISED", 30, "CONTRAINDICATED", 0, 365);
        AppropriatenessRequest worstCase = new AppropriatenessRequest("PROC-LAT", "DIALYSIS", "Left arm",
                null, LocalDate.now().minusDays(3), 20000, true, true, false, true,
                LocalDate.now().minusDays(1), List.of("PROC-OTHER"), List.of(), false, false, false);

        AppropriatenessVerdict v = engine.evaluate(TENANT, worstCase);

        assertThat(v.detectionCount()).isGreaterThan(5);
        assertThat(v.outcome()).isIn("BLOCKED", "PROCEED_WITH_CLARIFICATION");
        assertThat(v.outcome()).isNotEqualTo("CANCELLED");
        assertThat(v.detections()).allSatisfy(d -> {
            assertThat(d.suggestedAction()).as("every detection names a next action").isNotBlank();
            assertThat(d.disposition()).isIn("CLARIFY", "PROPOSE_ALTERNATIVE", "BLOCK");
        });
    }

    @Test
    void anUnavailableFacilityRoutesRatherThanCancels() {
        define("PROC-X", "MIDLINE", null, "NO_CONSTRAINT", null, null);
        AppropriatenessRequest r = new AppropriatenessRequest("PROC-X", null, null, null, null, null,
                null, null, true, null, null, null, null, false, null, null);

        AppropriatenessVerdict v = engine.evaluate(TENANT, r);

        assertThat(v.detections()).filteredOn(d -> d.code().equals("INAPPROPRIATE_FACILITY"))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.disposition()).isEqualTo("PROPOSE_ALTERNATIVE");
                    assertThat(d.suggestedAction()).contains("Refer");
                });
    }
}
