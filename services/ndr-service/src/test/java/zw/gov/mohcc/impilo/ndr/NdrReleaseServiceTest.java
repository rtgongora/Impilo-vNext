package zw.gov.mohcc.impilo.ndr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zw.gov.mohcc.impilo.ndr.client.DataGovernanceDeidClient;
import zw.gov.mohcc.impilo.ndr.client.DataGovernanceDeidClient.DeidRunResult;
import zw.gov.mohcc.impilo.ndr.client.DataGovernanceDeidClient.DeidUnavailableException;
import zw.gov.mohcc.impilo.ndr.domain.GoldEncounterEntity;
import zw.gov.mohcc.impilo.ndr.domain.ReleasedEncounterEntity;
import zw.gov.mohcc.impilo.ndr.repository.GoldEncounterRepository;
import zw.gov.mohcc.impilo.ndr.repository.ReleaseRunRepository;
import zw.gov.mohcc.impilo.ndr.repository.ReleasedEncounterRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2b / W2d (NDR side) — the released zone is built ONLY from de-id engine output,
 * NDR feeds the engine a tokenisable subject key and no direct identifiers, and the
 * engine's deny-by-default rejection materialises nothing.
 *
 * <p>The honest end-to-end de-identification proof over gold-shaped rows runs against
 * the REAL engine in {@code data-governance-service}
 * (DeidPipelineMockMvcTest#NdrGoldReleaseRegression). Here the engine client is a
 * double so this module can prove the wiring and released-zone materialisation.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class NdrReleaseServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = UUID.randomUUID().toString();
    private static final String DATASET_REF = "ndr-encounters-research";

    // Raw subject reference (a bronze/gold join key). Must never reach the released zone.
    private static final String PATIENT_REF = "PATIENT-REF-XYZ-001";
    // A plausible de-id engine token for that subject.
    private static final String SUBJECT_TOKEN =
            "a1b2c3d4e5f60718293a4b5c6d7e8f900112233445566778899aabbccddeeff00";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GoldEncounterRepository goldRepository;

    @Autowired
    private ReleaseRunRepository releaseRunRepository;

    @Autowired
    private ReleasedEncounterRepository releasedRepository;

    @MockBean
    private DataGovernanceDeidClient deidClient;

    @BeforeEach
    void reset() {
        releasedRepository.deleteAll();
        releaseRunRepository.deleteAll();
        goldRepository.deleteAll();
    }

    private void seedGoldEncounter(String patientRef, String facility) {
        GoldEncounterEntity g = new GoldEncounterEntity();
        g.setEncounterId(UUID.randomUUID());
        g.setTenantId(UUID.fromString(TENANT_ID));
        g.setPatientRef(patientRef);
        g.setFacilityRef(facility);
        g.setStatus("FINISHED");
        g.setSourceEventId("evt-" + UUID.randomUUID());
        g.setStartedAt(OffsetDateTime.parse("2026-07-03T10:00:00Z"));
        goldRepository.save(g);
    }

    private MockHttpServletRequestBuilder release(String datasetRef) {
        return post("/internal/v1/ndr/release/{ref}", datasetRef)
                .header("X-Tenant-ID", TENANT_ID)
                .header("X-Pod-ID", "national")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON);
    }

    @Nested
    @DisplayName("A) RELEASED — materialises a tokenised, de-identified analytics zone")
    class ReleasedPath {

        @Test
        @DisplayName("gold rows are sent to the engine as cpid-keyed rows with no direct identifiers")
        @SuppressWarnings("unchecked")
        void feedsEngineTokenisableSubjectAndNoPii() throws Exception {
            seedGoldEncounter(PATIENT_REF, "fac-001");

            when(deidClient.requestRun(eq(DATASET_REF), anyString(), any(), eq(TENANT_ID), anyString(), anyString()))
                    .thenReturn(released());

            mockMvc.perform(release(DATASET_REF)).andExpect(status().isAccepted());

            ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
            org.mockito.Mockito.verify(deidClient).requestRun(
                    eq(DATASET_REF), anyString(), captor.capture(), eq(TENANT_ID), anyString(), anyString());

            List<Map<String, Object>> sent = captor.getValue();
            assertThat(sent).hasSize(1);
            Map<String, Object> row = sent.get(0);
            // subject key carried as cpid so the engine tokenises it
            assertThat(row.get("cpid")).isEqualTo(PATIENT_REF);
            // NDR emits no direct identifiers and no separate raw join column
            assertThat(row).doesNotContainKeys("patient_ref", "name", "phone", "nid",
                    "healthId", "health_id", "source_event_id");
        }

        @Test
        @DisplayName("only tokenised engine output is materialised — no raw patient_ref survives")
        void materialisesReleasedZone() throws Exception {
            seedGoldEncounter(PATIENT_REF, "fac-001");
            when(deidClient.requestRun(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(released());

            MvcResult result = mockMvc.perform(release(DATASET_REF))
                    .andExpect(status().isAccepted())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.path("status").asText()).isEqualTo("RELEASED");
            assertThat(body.path("rows_out").asInt()).isEqualTo(1);
            assertThat(body.path("manifest_id").asText()).isNotBlank();

            List<ReleasedEncounterEntity> released = releasedRepository.findAll();
            assertThat(released).hasSize(1);
            ReleasedEncounterEntity row = released.get(0);
            assertThat(row.getSubjectToken()).isEqualTo(SUBJECT_TOKEN);
            assertThat(row.getDatasetRef()).isEqualTo(DATASET_REF);
            // the released zone never carries the raw subject reference
            assertThat(row.getReleasedJson()).doesNotContain(PATIENT_REF);
            assertThat(row.getReleasedJson()).contains("subject_token");
            assertThat(row.getReleasedJson()).contains("age_band");
        }

        @Test
        @DisplayName("re-running replaces the snapshot (does not accumulate duplicates)")
        void rebuildReplacesSnapshot() throws Exception {
            seedGoldEncounter(PATIENT_REF, "fac-001");
            when(deidClient.requestRun(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(released());

            mockMvc.perform(release(DATASET_REF)).andExpect(status().isAccepted());
            mockMvc.perform(release(DATASET_REF)).andExpect(status().isAccepted());

            assertThat(releasedRepository.countByTenantIdAndDatasetRef(
                    UUID.fromString(TENANT_ID), DATASET_REF)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("B) Deny-by-default — a rejected run materialises nothing")
    class DenyByDefault {

        @Test
        @DisplayName("engine REJECTED (no active policy) => status REJECTED, released zone empty")
        void rejectedMaterialisesNothing() throws Exception {
            seedGoldEncounter(PATIENT_REF, "fac-001");
            when(deidClient.requestRun(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(new DeidRunResult("REJECTED", UUID.randomUUID(), null, null,
                            1, 0, 0, "NO_ACTIVE_RELEASE_POLICY: ...", List.of()));

            MvcResult result = mockMvc.perform(release(DATASET_REF))
                    .andExpect(status().isAccepted())
                    .andReturn();

            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.path("status").asText()).isEqualTo("REJECTED");
            assertThat(body.path("rejection_reason").asText()).contains("NO_ACTIVE_RELEASE_POLICY");
            assertThat(releasedRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("no gold rows => build is skipped, engine never called")
        void noGoldSkips() throws Exception {
            MvcResult result = mockMvc.perform(release(DATASET_REF))
                    .andExpect(status().isAccepted())
                    .andReturn();
            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            assertThat(body.path("status").asText()).isEqualTo("NO_SOURCE_ROWS");
            org.mockito.Mockito.verifyNoInteractions(deidClient);
        }
    }

    @Nested
    @DisplayName("C) Engine unavailable => 503 (never a silent empty release)")
    class EngineUnavailable {

        @Test
        @DisplayName("DeidUnavailableException surfaces as 503 DEID_UNAVAILABLE")
        void engineDownReturns503() throws Exception {
            seedGoldEncounter(PATIENT_REF, "fac-001");
            when(deidClient.requestRun(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenThrow(new DeidUnavailableException("down", new RuntimeException()));

            mockMvc.perform(release(DATASET_REF)).andExpect(status().isServiceUnavailable());
        }
    }

    /** A realistic RELEASED result: one tokenised, generalised row, no raw subject value. */
    private DeidRunResult released() {
        Map<String, Object> releasedRow = new java.util.LinkedHashMap<>();
        releasedRow.put("subject_token", SUBJECT_TOKEN);
        releasedRow.put("age_band", "40-44");
        releasedRow.put("district", "Harare");
        releasedRow.put("visit_date", "2026-07");
        return new DeidRunResult("RELEASED", UUID.randomUUID(), UUID.randomUUID(), "1",
                1, 1, 0, null, List.of(releasedRow));
    }
}
