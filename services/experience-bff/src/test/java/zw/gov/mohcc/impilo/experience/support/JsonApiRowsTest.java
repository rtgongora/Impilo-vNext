package zw.gov.mohcc.impilo.experience.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixtures here are the payloads the upstream services actually emit, not the payloads the
 * TypeScript types wish they emitted. That distinction is the whole point: every consumer test in
 * the shell mocks its hook with a TS-shaped fixture, so the mocks encode the mismatch and the
 * suite stays green while the page white-screens. These assert against the real shape.
 */
class JsonApiRowsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Assert on the serialised response, not on the in-memory map. The values inside
     * {@code attributes} are {@link JsonNode}s, and a {@code TextNode}'s {@code toString()} keeps
     * its JSON quotes — so an in-memory assertion can pass or fail for reasons the browser never
     * sees. What the consumer receives is the wire shape, so that is what is checked here.
     */
    private JsonNode onTheWire(Object response) {
        try {
            return mapper.readTree(mapper.writeValueAsString(response));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Verbatim from {@code pct AllergyController.toMap} — flat, snake_case, no wrapper. */
    private static final String PCT_ALLERGIES = """
            [
              {"allergy_id":"a1","patient_id":"CPID-1","allergen":"Penicillin",
               "severity":"SEVERE","clinical_status":"ACTIVE","reaction":"Anaphylaxis",
               "onset_date":"2019-04-02","notes":null}
            ]
            """;

    /** Verbatim from {@code pct ImmunizationController} — an object, not an array. */
    private static final String PCT_IMMUNIZATIONS = """
            {"items":[{"immunization_id":"i1","vaccine_name":"BCG","status":"COMPLETED",
                       "occurrence_at":"2020-01-05T09:00:00Z","body_site":"left arm"}],
             "page":0,"size":20,"total":1}
            """;

    @Test
    void wrapsFlatUpstreamRowsSoTheDeclaredAttributesExist() {
        List<Map<String, Object>> rows = JsonApiRows.rows(json(PCT_ALLERGIES), "allergy", "allergy_id");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("id", "a1").containsEntry("type", "allergy");

        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) rows.get(0).get("attributes");
        assertThat(attributes).containsKeys("allergen", "severity", "clinical_status");
    }

    @Test
    void emitsBothDialectsSoACamelCaseHookAndASnakeCaseContractSeeTheSameRow() {
        JsonNode wire = onTheWire(JsonApiRows.rows(json(PCT_ALLERGIES), "allergy", "allergy_id"));
        JsonNode attributes = wire.get(0).get("attributes");

        // PatientBanner reads `.attributes.severity`; the create contract writes `clinical_status`.
        assertThat(attributes.get("severity").asText()).isEqualTo("SEVERE");
        assertThat(attributes.get("clinicalStatus").asText()).isEqualTo("ACTIVE");
        assertThat(attributes.get("clinical_status").asText()).isEqualTo("ACTIVE");
        assertThat(attributes.get("onsetDate").asText()).isEqualTo("2019-04-02");
        // A null upstream field must stay null, not become the string "null".
        assertThat(attributes.get("notes").isNull()).isTrue();
    }

    @Test
    void unwrapsAnItemsObjectSoAnArrayHookDoesNotGetAnObject() {
        // The regression: the hook declares an array, PCT returns {items,page,size,total}, and
        // `?? []` cannot save it because the object is non-nullish — `.filter is not a function`
        // fired on every single load of the immunizations tab.
        JsonNode wire = onTheWire(JsonApiRows.rows(json(PCT_IMMUNIZATIONS), "immunization", "immunization_id"));

        assertThat(wire.isArray()).as("the hook calls .filter on this").isTrue();
        assertThat(wire).hasSize(1);
        assertThat(wire.get(0).get("id").asText()).isEqualTo("i1");

        JsonNode attributes = wire.get(0).get("attributes");
        assertThat(attributes.get("vaccineName").asText()).isEqualTo("BCG");
        assertThat(attributes.get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void treatsAbsentAndEmptyUpstreamsAsAnEmptyListRatherThanThrowing() {
        assertThat(JsonApiRows.rows(null, "allergy")).isEmpty();
        assertThat(JsonApiRows.rows(json("null"), "allergy")).isEmpty();
        assertThat(JsonApiRows.rows(json("[]"), "allergy")).isEmpty();
        assertThat(JsonApiRows.rows(json("{\"items\":[]}"), "allergy")).isEmpty();
    }

    @Test
    void treatsALoneObjectAsOneRowRatherThanDroppingIt() {
        List<Map<String, Object>> rows =
                JsonApiRows.rows(json("{\"allergy_id\":\"a9\",\"allergen\":\"Latex\"}"), "allergy", "allergy_id");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("id", "a9");
    }

    @Test
    void alwaysResolvesAnIdBecauseReactKeysOffItAndANullIdCollapsesTheList() {
        // No declared id key matches; the trailing-_id fallback must still find one.
        List<Map<String, Object>> rows =
                JsonApiRows.rows(json("[{\"referral_id\":\"r7\",\"status\":\"PENDING\"}]"), "referral", "not_a_key");
        assertThat(rows.get(0).get("id")).isEqualTo("r7");
    }
}
