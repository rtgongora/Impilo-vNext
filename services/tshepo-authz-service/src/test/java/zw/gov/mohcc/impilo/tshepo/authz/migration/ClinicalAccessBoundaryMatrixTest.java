package zw.gov.mohcc.impilo.tshepo.authz.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.WorkMode;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V058 is the positive companion lock V055 deferred: a duty mode that does not carry
 * IDENTIFIED clinical access may not read a clinical record.
 *
 * <p><strong>Why this executes the migration instead of describing it.</strong> On a DENY
 * rule the conditions decide whether the DENY <em>fires</em>, so {@code allowed_modes} is
 * what expresses "deny these modes" and {@code blocked_modes} would express "deny everything
 * except these". Written the wrong way round, this migration would have denied clinical care
 * and permitted the couriers — and because {@code blocked_modes} treats a missing mode as
 * not-blocked, it would have fired on the token-less sessions that are currently almost all
 * clinical traffic. Reading the SQL does not reveal that; running it does.</p>
 *
 * <p><strong>Why the mode list is derived.</strong> Adding a {@link WorkMode} is a small
 * change in a Java enum with nothing to suggest that a SQL file seeded months earlier now
 * has a hole in it. Deriving the expectation makes the enum change the thing that fails.</p>
 */
class ClinicalAccessBoundaryMatrixTest extends MigrationPolicyTestBase {

    private static final String V058 = "V058__clinical_access_requires_identified_mode.sql";

    /**
     * The clinical families V058 locks — mirrors PolicyEngine's CLINICAL_RESOURCE_TYPES.
     *
     * <p>No trailing slash, and that is load-bearing: {@code pathContainsSegment} requires the
     * pin to be followed by {@code /} or end-of-path, so {@code "/patients/"} matches only the
     * bare collection and never {@code /v1/patients/{id}}. See {@link WorkModeBoundaryPinTest},
     * where the same defect in V055 is the thing under repair.</p>
     */
    private static final List<String> CLINICAL_PATHS = List.of(
            "/patients", "/encounters", "/observations",
            "/diagnostic-reports", "/medication-requests", "/prescriptions", "/clinical");

    @Test
    @DisplayName("every mode without identified clinical access is denied on every clinical family")
    void nonClinicalModesAreDeniedEverywhere() throws IOException {
        Set<String> nonClinical = modesWithoutIdentifiedClinicalAccess();
        assertThat(nonClinical)
                .as("if this is empty the derivation is broken, not the migration")
                .isNotEmpty();

        for (String family : CLINICAL_PATHS) {
            for (String mode : nonClinical) {
                assertThat(decide(family, dutyIn(WorkMode.valueOf(mode))).verdict())
                        .as("%s under %s must be denied", family, mode)
                        .isEqualTo(Verdict.DENY);
            }
        }
    }

    /**
     * The other half of the polarity. A lock that denies everything is not a lock, it is an
     * outage — and it is what writing {@code blocked_modes} here would have produced.
     */
    @Test
    @DisplayName("modes that DO carry identified clinical access still read the record")
    void clinicalModesAreUnaffected() throws IOException {
        List<WorkMode> clinical = Arrays.stream(WorkMode.values())
                .filter(WorkMode::grantsIdentifiedClinicalRead)
                .toList();
        assertThat(clinical).isNotEmpty();

        for (String family : CLINICAL_PATHS) {
            for (WorkMode mode : clinical) {
                assertThat(decide(family, dutyIn(mode)).verdict())
                        .as("%s under %s is the clinical work this lock exists to protect",
                                family, mode)
                        .isEqualTo(Verdict.ALLOW);
            }
        }
    }

    /**
     * The reason this is safe to seed during the SHADOW soak. Work-context minting is new and
     * {@code TSHEPO_WORK_CONTEXT_MODE} is still SHADOW, so most live clinical traffic carries
     * no duty token. A lock that fired on those sessions would take clinical access down
     * estate-wide the moment it was activated — which is exactly what putting
     * {@code requires_identified_clinical_access} on the live ALLOW rules would have done.
     */
    @Test
    @DisplayName("a session with no duty context is untouched — the reason this is safe to flip")
    void tokenlessSessionsAreUntouched() throws IOException {
        for (String family : CLINICAL_PATHS) {
            assertThat(decide(family, DutyContext.absent()).verdict())
                    .as("%s with no work-context token must be unaffected by the lock", family)
                    .isEqualTo(Verdict.ALLOW);
        }
    }

    @Test
    @DisplayName("seeded inactive, so applying the migration changes nothing until D7 flips it")
    void seededInactive() throws IOException {
        String sql = readMigration(V058);

        assertThat(sql.lines().filter(l -> l.contains("clinical-lock-")).count())
                .isEqualTo(CLINICAL_PATHS.size());
        assertThat(sql.lines()
                .map(String::strip)
                .filter(l -> l.equals("false),") || l.equals("false)"))
                .count())
                .as("a row seeded active=true would enforce on apply, defeating the staged cutover")
                .isEqualTo(CLINICAL_PATHS.size());
    }

    @Test
    @DisplayName("covers every clinical family, and every row is a DENY")
    void coversEveryClinicalFamily() throws IOException {
        assertThat(conditionsByPathPin().keySet())
                .containsExactlyInAnyOrderElementsOf(CLINICAL_PATHS);
        assertThat(readMigration(V058)).doesNotContain("'ALLOW'");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Set<String> modesWithoutIdentifiedClinicalAccess() {
        return Arrays.stream(WorkMode.values())
                .filter(m -> !m.grantsIdentifiedClinicalRead())
                .map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private AuthzResponse decide(String family, DutyContext duty) throws IOException {
        String conditions = conditionsByPathPin().get(family);
        assertThat(conditions).as("V058 has no rule for %s", family).isNotNull();
        return decideWithDeny(conditions, "/v1" + family + "/abc", duty);
    }

    /** Each V058 row's conditions JSON, keyed by the path family it pins. */
    private static Map<String, String> conditionsByPathPin() throws IOException {
        Matcher m = Pattern.compile("'(\\{\"path_contains\": \"([^\"]+)\".*?})'")
                .matcher(readMigration(V058));

        Map<String, String> byPin = new LinkedHashMap<>();
        while (m.find()) {
            byPin.put(m.group(2), m.group(1));
        }
        assertThat(byPin)
                .as("parsed nothing out of V058 — the test would pass vacuously")
                .isNotEmpty();
        return byPin;
    }
}
