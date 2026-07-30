package zw.gov.mohcc.impilo.tshepo.authz.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.WorkMode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V059 corrects V055's nine path pins, which were all written with a trailing slash.
 *
 * <p>{@code path_contains} is segment-bounded — the pin must be followed by {@code /} or
 * end-of-path — which is what stops {@code /patients} granting {@code /patientsummary}. The
 * same rule means {@code "/patients/"} is satisfied only by the bare collection
 * {@code /v1/patients/}, never by {@code /v1/patients/{id}}. So V055's boundary, as seeded,
 * would have reported enforcement at the D7 cutover while permitting every record access it
 * names.</p>
 *
 * <p>This test applies V059's own {@code replace()} pairs to V055's own rule text and then
 * runs both forms through the engine, so it proves three things at once: that the old form
 * was inert, that the new form fires, and that V059 is what turns one into the other. Any of
 * those asserted from a hand-written string would be an assertion about the test.</p>
 */
class WorkModeBoundaryPinTest extends MigrationPolicyTestBase {

    private static final String V055 = "V055__work_mode_boundary_policy_rules.sql";
    private static final String V059 = "V059__work_mode_boundary_path_pins_match_records.sql";

    /** A record under each family V055 pins — the access the boundary exists to refuse. */
    private static final Map<String, String> RECORD_PATH = Map.of(
            "work-mode-management-no-patient-record", "/v1/patients/p-123",
            "work-mode-support-no-clinical-read", "/v1/clinical/notes/n-1",
            "work-mode-support-no-encounter-read", "/v1/encounters/e-1",
            "work-mode-support-no-prescription-read", "/v1/prescriptions/rx-1",
            "work-mode-support-no-observation-read", "/v1/observations/o-1",
            "work-mode-regulator-no-facility-operation", "/v1/facility-mode/config",
            "work-mode-regulator-no-queue-operation", "/v1/queue/q-1/call-next",
            "work-mode-regulator-no-service-point-operation", "/v1/service-points/sp-1",
            "work-mode-regulator-no-workspace-operation", "/v1/workspaces/w-1");

    /** A mode each rule names in its own {@code allowed_modes}, so the DENY is in scope. */
    private static final Map<String, WorkMode> BLOCKED_MODE = Map.of(
            "work-mode-management-no-patient-record", WorkMode.FACILITY_MANAGEMENT,
            "work-mode-support-no-clinical-read", WorkMode.TECHNICAL_SUPPORT,
            "work-mode-support-no-encounter-read", WorkMode.TECHNICAL_SUPPORT,
            "work-mode-support-no-prescription-read", WorkMode.TECHNICAL_SUPPORT,
            "work-mode-support-no-observation-read", WorkMode.TECHNICAL_SUPPORT,
            "work-mode-regulator-no-facility-operation", WorkMode.REGULATORY_OPERATIONS,
            "work-mode-regulator-no-queue-operation", WorkMode.REGULATORY_OPERATIONS,
            "work-mode-regulator-no-service-point-operation", WorkMode.REGULATORY_OPERATIONS,
            "work-mode-regulator-no-workspace-operation", WorkMode.REGULATORY_OPERATIONS);

    @Test
    @DisplayName("V059 corrects every rule V055 seeded — none left behind")
    void v059CoversEveryV055Rule() throws IOException {
        Map<String, String> seeded = conditionsByRuleName(readMigration(V055));
        assertThat(v059Replacements().keySet())
                .as("a rule V059 forgets keeps a pin that matches no record")
                .containsExactlyInAnyOrderElementsOf(seeded.keySet());
        assertThat(seeded.keySet()).containsExactlyInAnyOrderElementsOf(RECORD_PATH.keySet());
    }

    @Test
    @DisplayName("as seeded, V055's rules do not fire on a record — the defect V059 fixes")
    void theSeededPinsAreInertOnRecords() throws IOException {
        Map<String, String> seeded = conditionsByRuleName(readMigration(V055));

        for (Map.Entry<String, String> rule : seeded.entrySet()) {
            AuthzResponse response = decideWithDeny(
                    rule.getValue(),
                    RECORD_PATH.get(rule.getKey()),
                    dutyIn(BLOCKED_MODE.get(rule.getKey())));

            assertThat(response.verdict())
                    .as("%s: if this is already DENY the pin was fine and V059 is unnecessary",
                            rule.getKey())
                    .isEqualTo(Verdict.ALLOW);
        }
    }

    @Test
    @DisplayName("after V059's replacement, the same rules deny the same records")
    void theCorrectedPinsFireOnRecords() throws IOException {
        Map<String, String> seeded = conditionsByRuleName(readMigration(V055));
        Map<String, Map.Entry<String, String>> fixes = v059Replacements();

        for (Map.Entry<String, String> rule : seeded.entrySet()) {
            Map.Entry<String, String> fix = fixes.get(rule.getKey());
            String corrected = rule.getValue().replace(fix.getKey(), fix.getValue());

            assertThat(corrected)
                    .as("%s: V059's replacement did not apply, so this proves nothing",
                            rule.getKey())
                    .isNotEqualTo(rule.getValue());

            AuthzResponse response = decideWithDeny(
                    corrected,
                    RECORD_PATH.get(rule.getKey()),
                    dutyIn(BLOCKED_MODE.get(rule.getKey())));

            assertThat(response.verdict())
                    .as("%s must refuse %s", rule.getKey(), RECORD_PATH.get(rule.getKey()))
                    .isEqualTo(Verdict.DENY);
        }
    }

    /**
     * The corrected pin must not have traded one defect for another. Segment bounding is the
     * reason the pin is a pin and not a substring match: {@code /patients} must not reach
     * {@code /patientsummary}.
     */
    @Test
    @DisplayName("the corrected pin is still segment-bounded, not a substring match")
    void correctionDoesNotOverreach() throws IOException {
        Map<String, String> seeded = conditionsByRuleName(readMigration(V055));
        String patientRule = "work-mode-management-no-patient-record";
        Map.Entry<String, String> fix = v059Replacements().get(patientRule);
        String corrected = seeded.get(patientRule).replace(fix.getKey(), fix.getValue());

        AuthzResponse neighbour = decideWithDeny(corrected, "/v1/patientsummary/s-1",
                dutyIn(WorkMode.FACILITY_MANAGEMENT));

        assertThat(neighbour.verdict())
                .as("/patients must not reach /patientsummary — that is what the bounding is for")
                .isEqualTo(Verdict.ALLOW);
    }

    /** Rule name to the {@code replace(conditions, from, to)} pair V059 applies to it. */
    private static Map<String, Map.Entry<String, String>> v059Replacements() throws IOException {
        Pattern stmt = Pattern.compile(
                "replace\\(conditions,\\s*'([^']+)',\\s*'([^']+)'\\)\\s*\\n\\s*WHERE name = '([^']+)'");
        Matcher m = stmt.matcher(readMigration(V059));

        Map<String, Map.Entry<String, String>> byName = new LinkedHashMap<>();
        while (m.find()) {
            byName.put(m.group(3), Map.entry(m.group(1), m.group(2)));
        }
        assertThat(byName)
                .as("parsed no UPDATEs out of V059 — the test would pass vacuously")
                .isNotEmpty();
        return byName;
    }
}
