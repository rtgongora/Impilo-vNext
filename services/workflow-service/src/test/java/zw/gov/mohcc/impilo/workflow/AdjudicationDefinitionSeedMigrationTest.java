package zw.gov.mohcc.impilo.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration-content test for V003__iatg_adjudication_definitions.sql.
 *
 * <p>The workflow-service test harness runs on H2 with Flyway disabled
 * (see application-test.yml), so the seed cannot be executed here. Instead we
 * assert the migration content directly: the three IATG definition codes are
 * present, the steps JSON parses, every doctrine state exists, and the
 * doctrine transition edges (including the EVIDENCE_REQUESTED &lt;-&gt;
 * UNDER_ADJUDICATION loop and APPEALED -&gt; UNDER_ADJUDICATION) are encoded.
 * The steps JSON shape is verified against WorkflowDefinitionEntity's
 * steps_json contract: a JSON array of objects each carrying "name" (the only
 * key the engine requires).</p>
 */
class AdjudicationDefinitionSeedMigrationTest {

    private static final String MIGRATION = "db/migration/V003__iatg_adjudication_definitions.sql";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> DEFINITION_CODES = List.of(
            "identity-claim-adjudication",
            "provider-claim-adjudication",
            "facility-claim-adjudication");

    private static final Set<String> DOCTRINE_STATES = Set.of(
            "DRAFT", "SUBMITTED", "TRIAGED", "EVIDENCE_REQUESTED", "UNDER_ADJUDICATION",
            "DECIDED_APPROVED", "DECIDED_DENIED", "DECIDED_CONDITIONAL", "APPEALED", "CLOSED");

    private static String sql;

    @BeforeAll
    static void loadMigration() throws Exception {
        try (InputStream in = AdjudicationDefinitionSeedMigrationTest.class
                .getClassLoader().getResourceAsStream(MIGRATION)) {
            assertThat(in).as("migration file %s must exist on the classpath", MIGRATION).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Seeds exactly the three IATG adjudication definition codes")
    void seedsThreeDefinitionCodes() {
        for (String code : DEFINITION_CODES) {
            assertThat(sql).contains("'" + code + "'");
        }
        assertThat(sql).contains("INSERT INTO wf_definitions");
    }

    @Test
    @DisplayName("Uses fixed definition ids referenced by the IATG contract doc")
    void usesFixedDefinitionIds() {
        assertThat(sql).contains("'ad1d0000-0000-4000-8000-000000000001'");
        assertThat(sql).contains("'ad1d0000-0000-4000-8000-000000000002'");
        assertThat(sql).contains("'ad1d0000-0000-4000-8000-000000000003'");
    }

    @Test
    @DisplayName("Definitions are seeded PUBLISHED (engine requires PUBLISHED to start instances) and idempotent")
    void seededPublishedAndIdempotent() {
        assertThat(sql).contains("'PUBLISHED'");
        assertThat(sql).contains("ON CONFLICT ON CONSTRAINT uq_wf_def_tenant_code_version DO NOTHING");
    }

    @Test
    @DisplayName("Each steps_json parses, carries all doctrine states, and encodes doctrine transitions")
    void stepsJsonCarriesDoctrineStateMachine() throws Exception {
        List<JsonNode> stepsArrays = extractStepsArrays();
        assertThat(stepsArrays).as("one steps_json array per seeded definition").hasSize(3);

        for (JsonNode steps : stepsArrays) {
            assertThat(steps.isArray()).isTrue();

            Map<String, JsonNode> byName = new HashMap<>();
            for (JsonNode step : steps) {
                // Engine contract: every step object must carry "name".
                assertThat(step.hasNonNull("name")).isTrue();
                byName.put(step.get("name").asText(), step);
            }
            assertThat(byName.keySet()).containsExactlyInAnyOrderElementsOf(DOCTRINE_STATES);

            // Doctrine edges.
            assertThat(transitions(byName, "DRAFT")).containsExactly("SUBMITTED");
            assertThat(transitions(byName, "SUBMITTED")).containsExactly("TRIAGED");
            assertThat(transitions(byName, "TRIAGED"))
                    .containsExactlyInAnyOrder("EVIDENCE_REQUESTED", "UNDER_ADJUDICATION");
            // EVIDENCE_REQUESTED <-> UNDER_ADJUDICATION loop.
            assertThat(transitions(byName, "EVIDENCE_REQUESTED")).contains("UNDER_ADJUDICATION");
            assertThat(transitions(byName, "UNDER_ADJUDICATION")).containsExactlyInAnyOrder(
                    "EVIDENCE_REQUESTED", "DECIDED_APPROVED", "DECIDED_DENIED", "DECIDED_CONDITIONAL");
            for (String decided : List.of("DECIDED_APPROVED", "DECIDED_DENIED", "DECIDED_CONDITIONAL")) {
                assertThat(transitions(byName, decided)).containsExactlyInAnyOrder("APPEALED", "CLOSED");
            }
            // Appeal loops back to adjudication; CLOSED is terminal.
            assertThat(transitions(byName, "APPEALED")).containsExactly("UNDER_ADJUDICATION");
            assertThat(transitions(byName, "CLOSED")).isEmpty();
        }
    }

    @Test
    @DisplayName("schema_json declares one subject type per definition (IDENTITY, PROVIDER, FACILITY)")
    void schemaJsonDeclaresSubjectTypes() throws Exception {
        Set<String> subjectTypes = new HashSet<>();
        Matcher m = Pattern.compile("'(\\{\"stateMachine\".*?\\})'::jsonb").matcher(sql);
        while (m.find()) {
            JsonNode schema = MAPPER.readTree(m.group(1));
            assertThat(schema.get("initialState").asText()).isEqualTo("DRAFT");
            assertThat(schema.get("terminalStates").get(0).asText()).isEqualTo("CLOSED");
            subjectTypes.add(schema.get("subjectType").asText());
        }
        assertThat(subjectTypes).containsExactlyInAnyOrder("IDENTITY", "PROVIDER", "FACILITY");
    }

    private static List<JsonNode> extractStepsArrays() throws Exception {
        List<JsonNode> out = new ArrayList<>();
        Matcher m = Pattern.compile("'(\\[\\{.*?\\}\\])'::jsonb", Pattern.DOTALL).matcher(sql);
        while (m.find()) {
            out.add(MAPPER.readTree(m.group(1)));
        }
        return out;
    }

    private static List<String> transitions(Map<String, JsonNode> byName, String state) {
        JsonNode step = byName.get(state);
        assertThat(step).as("state %s must be present", state).isNotNull();
        List<String> out = new ArrayList<>();
        step.path("transitions").forEach(t -> out.add(t.asText()));
        return out;
    }
}
