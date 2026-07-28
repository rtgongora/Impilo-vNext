package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins each clinical list endpoint's response against the shape its TypeScript hook declares,
 * driven by the payload the upstream service really returns.
 *
 * <p>WHY A SECOND TEST. {@code JsonApiRowsTest} proves the normaliser works; it cannot prove any
 * controller calls it. A controller that reverts to {@code Map.of("data", pctData)} leaves that
 * suite green while the page white-screens — a check outliving the thing it guards. These tests
 * fail if the call is removed, which is the only property that makes them worth running.
 *
 * <p>Note what is deliberately NOT done here: the upstream fixtures are copied from the upstream
 * controllers' own output, not from the hook's declared type. Every consumer test in the shell
 * mocks its hook with a TS-shaped fixture, which is precisely why the suite was green while the
 * immunizations tab threw on every load.
 */
class ClinicalListShapeContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode wireBody(ResponseEntity<Map<String, Object>> response) {
        try {
            return mapper.readTree(mapper.writeValueAsString(response.getBody()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void allergyListCarriesAttributesBecausePatientBannerRendersOnEveryEhrScreen() {
        PctServiceClient pct = mock(PctServiceClient.class);
        // Verbatim from pct AllergyController.toMap — flat, snake_case, no `attributes` wrapper.
        when(pct.listAllergies(anyString())).thenReturn(json("""
                [{"allergy_id":"a1","patient_id":"CPID-1","allergen":"Penicillin",
                  "severity":"SEVERE","clinical_status":"ACTIVE","reaction":"Anaphylaxis"}]
                """));

        JsonNode body = wireBody(new AllergiesController(pct)
                .listAllergies("tenant", "req", "corr", "CPID-1"));
        JsonNode data = body.get("data");

        assertThat(data.isArray()).isTrue();
        JsonNode attributes = data.get(0).get("attributes");
        assertThat(attributes)
                .as("useAllergies declares attributes.severity; PatientBanner:110 dereferences it "
                        + "unguarded, so a missing wrapper crashes the banner on exactly the "
                        + "patients who have a severe allergy recorded")
                .isNotNull();
        assertThat(attributes.get("severity").asText()).isEqualTo("SEVERE");
        assertThat(attributes.get("allergen").asText()).isEqualTo("Penicillin");
        assertThat(data.get(0).get("id").asText()).isEqualTo("a1");
    }

    @Test
    void immunizationListIsAnArrayBecauseTheHookCallsFilterOnIt() {
        PctServiceClient pct = mock(PctServiceClient.class);
        // Verbatim from pct ImmunizationController — a paged OBJECT, not an array.
        when(pct.listImmunizations(anyString(), anyInt(), anyInt())).thenReturn(json("""
                {"items":[{"immunization_id":"i1","vaccine_name":"BCG","status":"COMPLETED",
                           "occurrence_at":"2020-01-05T09:00:00Z"}],
                 "page":0,"size":20,"total":1}
                """));

        JsonNode body = wireBody(new ImmunizationsController(pct)
                .listImmunizations("tenant", "req", "corr", 0, 20, "CPID-1"));

        assertThat(body.get("data").isArray())
                .as("the immunizations page does `immunizationsData?.data ?? []` then `.filter(...)`; "
                        + "an object is non-nullish so `?? []` cannot rescue it and the page threw "
                        + "`.filter is not a function` on every load")
                .isTrue();
        assertThat(body.get("data").get(0).get("attributes").get("vaccineName").asText()).isEqualTo("BCG");
        // The paging counters are still reachable — moved to meta, not discarded.
        assertThat(body.get("meta").get("total").asLong()).isEqualTo(1L);
    }

    /**
     * Verbatim from pct {@code EncounterController.getTimeline} — journeys with their encounters
     * nested inside. Both {@code /encounters} and {@code /timeline} read this one payload.
     */
    private static final String PCT_JOURNEY_TIMELINE = """
            [{"journey":{"journeyId":"J-1","patientCpid":"CPID-1","facilityId":"F-1",
                         "referralId":"REF-9","referralSource":"Chitungwiza Central",
                         "createdAt":"2026-03-01T08:00:00Z"},
              "encounters":[{"id":41,"encounterRef":"ENC-1","journeyId":"J-1",
                             "subjectCpid":"CPID-1","facilityId":"F-1","status":"STARTED",
                             "encounterType":"OUTPATIENT","assignedProviderId":"PRV-3",
                             "startedAt":"2026-03-01T08:05:00Z","endedAt":null}]}]
            """;

    @Test
    void encounterListIsFlatBecauseThirtyFivePagesAndTheBannerDereferenceItDirectly() {
        PctServiceClient pct = mock(PctServiceClient.class);
        when(pct.getPatientTimeline(anyString())).thenReturn(json(PCT_JOURNEY_TIMELINE));

        JsonNode body = wireBody(new EncounterController(pct, null, null)
                .listEncounters("tenant", "req", "corr", 0, 20, "CPID-1"));
        JsonNode data = body.get("data");

        assertThat(data.isArray()).isTrue();
        assertThat(data).as("the encounters must be lifted out of their journeys").hasSize(1);

        JsonNode attributes = data.get(0).get("attributes");
        assertThat(attributes.get("status").asText()).isEqualTo("STARTED");
        assertThat(data.get(0).get("id").asText()).isEqualTo("ENC-1");
        // The hook types these names; PCT calls them subjectCpid / assignedProviderId / endedAt.
        assertThat(attributes.get("patientId").asText()).isEqualTo("CPID-1");
        assertThat(attributes.get("providerId").asText()).isEqualTo("PRV-3");
        assertThat(attributes.get("closedAt").isNull()).isTrue();
        // Flattening must not lose the grouping — callers regroup without a second PCT round-trip.
        assertThat(attributes.get("journeyId").asText()).isEqualTo("J-1");
    }

    @Test
    void timelineDerivesEventsIncludingTheReferralTheTimelinePageFiltersOn() {
        PctServiceClient pct = mock(PctServiceClient.class);
        when(pct.getPatientTimeline(anyString())).thenReturn(json(PCT_JOURNEY_TIMELINE));

        JsonNode body = wireBody(new ClinicalTimelineController(pct)
                .listTimeline("tenant", "req", "corr", 0, 20, "CPID-1", null));
        JsonNode data = body.get("data");

        assertThat(data.isArray()).isTrue();
        java.util.List<String> eventTypes = new java.util.ArrayList<>();
        data.forEach(entry -> eventTypes.add(entry.get("attributes").get("eventType").asText()));

        assertThat(eventTypes)
                .as("timeline/page.tsx filters entries on eventType === \"REFERRAL\"")
                .contains("REFERRAL", "JOURNEY_OPENED", "ENCOUNTER");

        JsonNode encounterEvent = null;
        for (JsonNode entry : data) {
            if ("ENCOUNTER".equals(entry.get("attributes").get("eventType").asText())) {
                encounterEvent = entry;
            }
        }
        assertThat(encounterEvent).isNotNull();
        assertThat(encounterEvent.get("attributes").get("occurredAt").asText())
                .isEqualTo("2026-03-01T08:05:00Z");
        assertThat(encounterEvent.get("attributes").get("encounterId").asText()).isEqualTo("ENC-1");
        // No actor name is available upstream; inventing one would print an id where a person goes.
        assertThat(encounterEvent.get("attributes").get("actorName").isNull()).isTrue();
    }

    @Test
    void aPatientWithNoJourneysGetsAnEmptyTimelineNotAFabricatedOne() {
        PctServiceClient pct = mock(PctServiceClient.class);
        when(pct.getPatientTimeline(anyString())).thenReturn(json("[]"));

        assertThat(wireBody(new EncounterController(pct, null, null)
                .listEncounters("tenant", "req", "corr", 0, 20, "CPID-1")).get("data")).isEmpty();
        assertThat(wireBody(new ClinicalTimelineController(pct)
                .listTimeline("tenant", "req", "corr", 0, 20, "CPID-1", null)).get("data")).isEmpty();
    }

    @Test
    void anEmptyUpstreamStillYieldsAnArrayRatherThanNothingTheHookCanIterate() {
        PctServiceClient pct = mock(PctServiceClient.class);
        when(pct.listAllergies(anyString())).thenReturn(json("[]"));
        when(pct.listImmunizations(anyString(), anyInt(), anyInt())).thenReturn(json("{\"items\":[],\"total\":0}"));

        assertThat(wireBody(new AllergiesController(pct)
                .listAllergies("tenant", "req", "corr", "CPID-1")).get("data")).isEmpty();
        assertThat(wireBody(new ImmunizationsController(pct)
                .listImmunizations("tenant", "req", "corr", 0, 20, "CPID-1")).get("data")).isEmpty();
    }
}
