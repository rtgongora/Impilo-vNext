package zw.gov.mohcc.impilo.datagovernance.deid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import zw.gov.mohcc.impilo.datagovernance.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.datagovernance.repository.OutboxEventRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end de-identification pipeline proof over the REST surface
 * (design test bar: docs/data-platform/de-identification-pipeline.md).
 *
 * <p>Dataset secrets are resolved by reference from application-test.yml
 * ({@code test-secret-a} / {@code test-secret-b}); production custody is
 * tshepo-keys.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DeidPipelineMockMvcTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT_ID = UUID.randomUUID().toString();

    // PII smuggled into every source row — none of it may ever come out.
    private static final String PII_NAME = "Tariro Moyo";
    private static final String PII_HEALTH_ID = "ZW-HID-990011";
    private static final String PII_NID = "63-123456A78";
    private static final String PII_PHONE = "+263771234567";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxRepository;

    // ── A) Happy path: request → transforms → QA → gate → RELEASED ──

    @Nested
    @DisplayName("A) FSM happy path releases a de-identified dataset")
    class HappyPath {

        @Test
        @DisplayName("run is RELEASED with manifest, k-anon suppression and full outbox audit")
        void releasedRunEndToEnd() throws Exception {
            String policyCode = "pol-happy-" + System.nanoTime();
            String datasetCode = "ds-happy-" + System.nanoTime();
            upsertActiveResearchPolicy(policyCode, 2);
            createDataset(datasetCode, policyCode, "test-secret-a", "RELEASED");

            JsonNode run = requestRun(datasetCode, happyRows());

            assertThat(run.path("status").asText()).isEqualTo("RELEASED");
            assertThat(run.path("rows_in").asInt()).isEqualTo(5);
            assertThat(run.path("rows_out").asInt()).isEqualTo(4);
            assertThat(run.path("rows_suppressed").asInt()).isEqualTo(1);
            assertThat(run.path("manifest_id").asText()).isNotBlank();
            assertThat(run.path("qa_report").path("min_retained_class_size").asInt())
                    .isGreaterThanOrEqualTo(2);
            assertThat(run.path("qa_report").path("k_threshold").asInt()).isEqualTo(2);

            // Every released row is tokenised + generalised
            JsonNode rows = run.path("released_rows");
            assertThat(rows.size()).isEqualTo(4);
            for (JsonNode row : rows) {
                assertThat(row.path("subject_token").asText()).matches("[0-9a-f]{64}");
                assertThat(row.path("age_band").asText()).isEqualTo("35-39");
                assertThat(row.path("district").asText()).isEqualTo("Harare");
                assertThat(row.path("visit_date").asText()).matches("\\d{4}-\\d{2}");
                assertThat(row.has("dob")).isFalse();
            }

            // GET returns the terminal state + QA report (row values are never persisted)
            String runId = run.path("run_id").asText();
            JsonNode fetched = getRun(runId);
            assertThat(fetched.path("status").asText()).isEqualTo("RELEASED");
            assertThat(fetched.path("qa_report").path("rows_suppressed").asInt()).isEqualTo(1);
            assertThat(fetched.path("manifest_id").asText()).isNotBlank();
            assertThat(fetched.path("completed_at").asText()).isNotBlank();

            // Full audit: requested → TRANSFORMING → QA → RELEASED in the outbox
            List<OutboxEventEntity> events = runEvents(runId);
            assertThat(events)
                    .extracting(OutboxEventEntity::getEventType)
                    .contains("impilo.data.governance.deid.run.requested.v1",
                            "impilo.data.governance.deid.run.state.changed.v1",
                            "impilo.data.governance.deid.run.released.v1");
            assertThat(events.stream()
                    .filter(e -> e.getEventType().endsWith("state.changed.v1"))
                    .count()).isEqualTo(2);   // TRANSFORMING + QA

            // Outbox payloads carry counts/codes only — never PII
            for (OutboxEventEntity event : events) {
                assertNoPii(event.getPayloadJson());
            }
        }
    }

    // ── B) PII smuggling: identifiers pushed at each zone must never come out ──

    @Nested
    @DisplayName("B) PII-smuggling regression — names/NIDs/CPIDs/phones never survive")
    class PiiSmuggling {

        @Test
        @DisplayName("RELEASED zone output carries no smuggled identifier, even when allowlisted")
        void releasedZoneStripsSmuggledPii() throws Exception {
            String policyCode = "pol-smuggle-rel-" + System.nanoTime();
            String datasetCode = "ds-smuggle-rel-" + System.nanoTime();
            upsertActiveResearchPolicy(policyCode, 2);   // allowlist includes name/healthId/cpid on purpose
            createDataset(datasetCode, policyCode, "test-secret-a", "RELEASED");

            String body = requestRunRaw(datasetCode, happyRows());
            assertNoPii(body);

            JsonNode run = MAPPER.readTree(body);
            for (JsonNode row : run.path("released_rows")) {
                assertThat(row.has("healthId")).isFalse();
                assertThat(row.has("health_id")).isFalse();
                assertThat(row.has("name")).isFalse();
                assertThat(row.has("cpid")).isFalse();
                assertThat(row.has("nid")).isFalse();
                assertThat(row.has("phone")).isFalse();
            }
        }

        @Test
        @DisplayName("TOKENISED zone (no generalisation) still strips every direct identifier")
        void tokenisedZoneStripsSmuggledPii() throws Exception {
            String policyCode = "pol-smuggle-tok-" + System.nanoTime();
            String datasetCode = "ds-smuggle-tok-" + System.nanoTime();
            upsertPolicy(policyCode, "RESEARCH", "ACTIVE", 2,
                    List.of("gender", "name", "healthId", "cpid"),
                    Map.of("quasiColumns", List.of()));
            createDataset(datasetCode, policyCode, "test-secret-a", "TOKENISED");

            List<Map<String, Object>> rows = List.of(
                    piiRow("CPID-701", "1990-01-01"), piiRow("CPID-702", "1990-01-01"));
            String body = requestRunRaw(datasetCode, rows);
            assertNoPii(body);

            JsonNode run = MAPPER.readTree(body);
            assertThat(run.path("status").asText()).isEqualTo("RELEASED");
            for (JsonNode row : run.path("released_rows")) {
                assertThat(row.has("healthId")).isFalse();
                assertThat(row.path("subject_token").asText()).matches("[0-9a-f]{64}");
                assertThat(row.path("gender").asText()).isEqualTo("F");
            }
        }
    }

    // ── C) Dataset-scoped tokens ──

    @Nested
    @DisplayName("C) Tokens are dataset-scoped (per-dataset secret)")
    class DatasetScopedTokens {

        @Test
        @DisplayName("same cpid → same token within a dataset, different token across datasets")
        void sameCpidUnlinkableAcrossDatasets() throws Exception {
            String policyCode = "pol-scope-" + System.nanoTime();
            upsertPolicy(policyCode, "RESEARCH", "ACTIVE", 2,
                    List.of("gender"), Map.of("quasiColumns", List.of()));
            String datasetA = "ds-scope-a-" + System.nanoTime();
            String datasetB = "ds-scope-b-" + System.nanoTime();
            createDataset(datasetA, policyCode, "test-secret-a", "TOKENISED");
            createDataset(datasetB, policyCode, "test-secret-b", "TOKENISED");

            List<Map<String, Object>> rows = List.of(
                    piiRow("CPID-SHARED", "1990-01-01"), piiRow("CPID-SHARED", "1990-01-01"));

            JsonNode runA = requestRun(datasetA, rows);
            JsonNode runB = requestRun(datasetB, rows);

            String tokenA1 = runA.path("released_rows").get(0).path("subject_token").asText();
            String tokenA2 = runA.path("released_rows").get(1).path("subject_token").asText();
            String tokenB1 = runB.path("released_rows").get(0).path("subject_token").asText();

            assertThat(tokenA1).isEqualTo(tokenA2);          // deterministic within dataset
            assertThat(tokenA1).isNotEqualTo(tokenB1);       // unlinkable across datasets
            assertThat(tokenA1).isNotEqualTo("CPID-SHARED"); // never the cpid
        }
    }

    // ── D) Policy gate: deny by default ──

    @Nested
    @DisplayName("D) Policy gate — deny by default, rejection recorded + audited")
    class PolicyGateRejections {

        @Test
        @DisplayName("no release policy at all => run REJECTED with reason, nothing released")
        void noPolicyRejected() throws Exception {
            String datasetCode = "ds-nopolicy-" + System.nanoTime();
            createDataset(datasetCode, "pol-does-not-exist-" + System.nanoTime(),
                    "test-secret-a", "RELEASED");

            JsonNode run = requestRun(datasetCode,
                    List.of(piiRow("CPID-801", "1990-01-01"), piiRow("CPID-802", "1990-01-01")));

            assertThat(run.path("status").asText()).isEqualTo("REJECTED");
            assertThat(run.path("rejection_reason").asText())
                    .contains("NO_ACTIVE_RELEASE_POLICY");
            assertThat(run.path("released_rows").isEmpty()).isTrue();
            assertThat(run.path("manifest_id").isNull()).isTrue();

            List<OutboxEventEntity> events = runEvents(run.path("run_id").asText());
            assertThat(events)
                    .extracting(OutboxEventEntity::getEventType)
                    .contains("impilo.data.governance.deid.run.rejected.v1");
        }

        @Test
        @DisplayName("DRAFT policy does not authorise a release")
        void draftPolicyRejected() throws Exception {
            String policyCode = "pol-draft-" + System.nanoTime();
            String datasetCode = "ds-draft-" + System.nanoTime();
            upsertPolicy(policyCode, "RESEARCH", "DRAFT", 2,
                    List.of("gender"), Map.of("quasiColumns", List.of()));
            createDataset(datasetCode, policyCode, "test-secret-a", "RELEASED");

            JsonNode run = requestRun(datasetCode,
                    List.of(piiRow("CPID-811", "1990-01-01"), piiRow("CPID-812", "1990-01-01")));

            assertThat(run.path("status").asText()).isEqualTo("REJECTED");
            assertThat(run.path("rejection_reason").asText()).contains("POLICY_NOT_ACTIVE");
        }

        @Test
        @DisplayName("activating the policy lets the same dataset release")
        void activatedPolicyReleases() throws Exception {
            String policyCode = "pol-activate-" + System.nanoTime();
            String datasetCode = "ds-activate-" + System.nanoTime();
            upsertPolicy(policyCode, "RESEARCH", "DRAFT", 2,
                    List.of("gender"), Map.of("quasiColumns", List.of()));
            createDataset(datasetCode, policyCode, "test-secret-a", "TOKENISED");

            List<Map<String, Object>> rows = List.of(
                    piiRow("CPID-821", "1990-01-01"), piiRow("CPID-822", "1990-01-01"));
            assertThat(requestRun(datasetCode, rows).path("status").asText()).isEqualTo("REJECTED");

            JsonNode updated = upsertPolicy(policyCode, "RESEARCH", "ACTIVE", 2,
                    List.of("gender"), Map.of("quasiColumns", List.of()));
            assertThat(updated.path("version").asInt()).isEqualTo(2);   // upsert bumps version

            assertThat(requestRun(datasetCode, rows).path("status").asText()).isEqualTo("RELEASED");
        }
    }

    // ── E) Rejection paths inside the transform stage (fail-closed) ──

    @Nested
    @DisplayName("E) Transform failures reject the run instead of leaking")
    class TransformRejections {

        @Test
        @DisplayName("source row without a cpid => REJECTED MISSING_SUBJECT_KEY")
        void missingCpidRejected() throws Exception {
            String policyCode = "pol-nocpid-" + System.nanoTime();
            String datasetCode = "ds-nocpid-" + System.nanoTime();
            upsertActiveResearchPolicy(policyCode, 2);
            createDataset(datasetCode, policyCode, "test-secret-a", "RELEASED");

            Map<String, Object> rowWithoutCpid = new LinkedHashMap<>();
            rowWithoutCpid.put("gender", "F");
            rowWithoutCpid.put("dob", "1990-01-01");
            JsonNode run = requestRun(datasetCode, List.of(rowWithoutCpid));

            assertThat(run.path("status").asText()).isEqualTo("REJECTED");
            assertThat(run.path("rejection_reason").asText()).contains("MISSING_SUBJECT_KEY");
        }

        @Test
        @DisplayName("unresolvable dataset secret => REJECTED SECRET_UNAVAILABLE (never tokenises)")
        void unresolvableSecretRejected() throws Exception {
            String policyCode = "pol-nosecret-" + System.nanoTime();
            String datasetCode = "ds-nosecret-" + System.nanoTime();
            upsertActiveResearchPolicy(policyCode, 2);
            createDataset(datasetCode, policyCode, "secret-ref-not-configured", "RELEASED");

            JsonNode run = requestRun(datasetCode, List.of(piiRow("CPID-901", "1990-01-01")));

            assertThat(run.path("status").asText()).isEqualTo("REJECTED");
            assertThat(run.path("rejection_reason").asText()).contains("SECRET_UNAVAILABLE");
        }
    }

    // ── F) Registration validation + lookups ──

    @Nested
    @DisplayName("F) Dataset registration and lookups")
    class RegistrationAndLookups {

        @Test
        @DisplayName("RAW can never be a materialisation target")
        void rawZoneRejected() throws Exception {
            mockMvc.perform(withHeaders(post("/internal/v1/deid/datasets"))
                            .content(datasetBody("ds-raw-" + System.nanoTime(), null,
                                    "test-secret-a", "RAW", "RESEARCH")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("non-analytics purposes are rejected")
        void invalidPurposeRejected() throws Exception {
            mockMvc.perform(withHeaders(post("/internal/v1/deid/datasets"))
                            .content(datasetBody("ds-purpose-" + System.nanoTime(), null,
                                    "test-secret-a", "RELEASED", "TREATMENT")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("duplicate dataset codes are rejected")
        void duplicateDatasetCodeRejected() throws Exception {
            String datasetCode = "ds-dup-" + System.nanoTime();
            createDataset(datasetCode, null, "test-secret-a", "RELEASED");
            mockMvc.perform(withHeaders(post("/internal/v1/deid/datasets"))
                            .content(datasetBody(datasetCode, null, "test-secret-a",
                                    "RELEASED", "RESEARCH")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("registered datasets are listable with their secret REFERENCE only")
        void listDatasets() throws Exception {
            String datasetCode = "ds-list-" + System.nanoTime();
            createDataset(datasetCode, null, "test-secret-a", "RELEASED");

            MvcResult result = mockMvc.perform(withHeaders(get("/internal/v1/deid/datasets")))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode datasets = MAPPER.readTree(result.getResponse().getContentAsString());
            JsonNode match = null;
            for (JsonNode dataset : datasets) {
                if (datasetCode.equals(dataset.path("dataset_code").asText())) {
                    match = dataset;
                }
            }
            assertThat(match).isNotNull();
            assertThat(match.path("secret_ref").asText()).isEqualTo("test-secret-a");
            // the secret material itself must never appear (custody: tshepo-keys)
            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("unit-test-dataset-secret-a");
        }

        @Test
        @DisplayName("unknown run id => 404")
        void unknownRunNotFound() throws Exception {
            mockMvc.perform(withHeaders(get("/internal/v1/deid/runs/" + UUID.randomUUID())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("running against an unregistered dataset => 400")
        void unknownDatasetRejected() throws Exception {
            mockMvc.perform(withHeaders(post("/internal/v1/deid/runs"))
                            .content(MAPPER.writeValueAsString(Map.of(
                                    "dataset_code", "ds-never-registered",
                                    "requested_by", "analyst-1",
                                    "source_rows", List.of(piiRow("CPID-1", "1990-01-01"))))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── G) NDR gold→release leak regression (W2d) ──

    @Nested
    @DisplayName("G) NDR gold→release build — the released analytics zone leaks no PII")
    class NdrGoldReleaseRegression {

        // A raw subject reference that also joins the NDR bronze zone.
        private static final String JOIN_PATIENT_REF_1 = "PATIENT-REF-0001";

        @Test
        @DisplayName("gold rows (direct IDs, FHIR-extension IDs, free-text, bronze join key, small cell) release clean")
        void releaseBuildProducesCleanDeidentifiedZone() throws Exception {
            String policyCode = "pol-ndr-rel-" + System.nanoTime();
            String datasetCode = "ds-ndr-rel-" + System.nanoTime();
            // Deliberately allowlist identifier-named columns to prove name-based
            // stripping beats a mis-configured allowlist. The raw bronze join
            // columns (patient_ref, source_event_id) are NOT allowlisted → dropped.
            upsertPolicy(policyCode, "RESEARCH", "ACTIVE", 2,
                    List.of("dob", "district", "visit_date", "diagnosis", "gender",
                            "name", "cpid", "extensionCpid", "patientHealthId"),
                    Map.of("columns", Map.of(
                                    "dob", "AGE_BAND_5",
                                    "district", "DISTRICT",
                                    "visit_date", "MONTH",
                                    "diagnosis", "RARE_TO_OTHER"),
                            "quasiColumns", List.of("age_band", "district"),
                            "rareThreshold", 2));
            createDataset(datasetCode, policyCode, "test-secret-a", "RELEASED");

            String body = requestRunRaw(datasetCode, ndrGoldRows());
            assertNoPii(body);
            // No raw subject/join key survives anywhere in the released payload.
            assertThat(body).doesNotContain("PATIENT-REF");
            assertThat(body).doesNotContain("evt-ndr-");

            JsonNode run = MAPPER.readTree(body);
            assertThat(run.path("status").asText()).isEqualTo("RELEASED");
            assertThat(run.path("rows_in").asInt()).isEqualTo(5);
            assertThat(run.path("rows_out").asInt()).isEqualTo(4);
            assertThat(run.path("rows_suppressed").asInt()).isEqualTo(1);   // singleton class suppressed
            assertThat(run.path("qa_report").path("min_retained_class_size").asInt())
                    .isGreaterThanOrEqualTo(2);                              // k-anonymity holds

            JsonNode rows = run.path("released_rows");
            assertThat(rows.size()).isEqualTo(4);
            for (JsonNode row : rows) {
                // tokenised subject, never the raw patient reference
                assertThat(row.path("subject_token").asText()).matches("[0-9a-f]{64}");
                // direct identifiers gone (even the allowlisted / FHIR-extension ones)
                assertThat(row.has("patient_ref")).isFalse();
                assertThat(row.has("source_event_id")).isFalse();
                assertThat(row.has("name")).isFalse();
                assertThat(row.has("cpid")).isFalse();
                assertThat(row.has("nid")).isFalse();
                assertThat(row.has("phone")).isFalse();
                assertThat(row.has("healthId")).isFalse();
                assertThat(row.has("extensionCpid")).isFalse();
                assertThat(row.has("patientHealthId")).isFalse();
                // quasi-identifiers generalised, raw dob gone
                assertThat(row.has("dob")).isFalse();
                assertThat(row.path("age_band").asText()).isEqualTo("40-44");
                assertThat(row.path("district").asText()).isEqualTo("Harare");
                assertThat(row.path("visit_date").asText()).matches("\\d{4}-\\d{2}");
            }
        }

        @Test
        @DisplayName("a bronze join key smuggled into an allowlisted non-identifier column is caught (REJECTED)")
        void bronzeJoinKeySmuggledIntoAllowlistedColumnRejects() throws Exception {
            String policyCode = "pol-ndr-join-" + System.nanoTime();
            String datasetCode = "ds-ndr-join-" + System.nanoTime();
            // region_code is not an identifier by NAME, and it is allowlisted — but it
            // carries the raw subject value, which is a re-identification join key.
            upsertPolicy(policyCode, "RESEARCH", "ACTIVE", 2,
                    List.of("gender", "region_code"), Map.of("quasiColumns", List.of()));
            createDataset(datasetCode, policyCode, "test-secret-a", "TOKENISED");

            Map<String, Object> row1 = new LinkedHashMap<>();
            row1.put("cpid", JOIN_PATIENT_REF_1);
            row1.put("gender", "F");
            row1.put("region_code", JOIN_PATIENT_REF_1);   // raw subject value smuggled through
            Map<String, Object> row2 = new LinkedHashMap<>();
            row2.put("cpid", "PATIENT-REF-0002");
            row2.put("gender", "M");
            row2.put("region_code", "PATIENT-REF-0002");

            JsonNode run = requestRun(datasetCode, List.of(row1, row2));

            assertThat(run.path("status").asText()).isEqualTo("REJECTED");
            assertThat(run.path("rejection_reason").asText()).contains("PII_LEAK_DETECTED");
            assertThat(run.path("released_rows").isEmpty()).isTrue();
        }

        /** 4 rows in one equivalence class (40-44 / Harare) + 1 singleton (suppressed). */
        private List<Map<String, Object>> ndrGoldRows() {
            List<Map<String, Object>> rows = new ArrayList<>();
            java.time.LocalDate now = java.time.LocalDate.now();
            int[] ages = {40, 41, 42, 43};   // all in the 40-44 band
            for (int i = 0; i < ages.length; i++) {
                rows.add(ndrGoldRow("PATIENT-REF-100" + i,
                        now.minusYears(ages[i]).minusMonths(1).toString(),
                        "Harare Province/Harare/Mbare Clinic", "TB"));
            }
            rows.add(ndrGoldRow("PATIENT-REF-1009",
                    now.minusYears(80).minusMonths(1).toString(),
                    "Manicaland/Mutare/Sakubva Clinic", "RARE-SYNDROME"));   // singleton class
            return rows;
        }

        /**
         * A gold-encounter row flattened for a de-id run: the subject reference doubles
         * as the {@code cpid} key, and it also appears as the raw {@code patient_ref} /
         * {@code source_event_id} bronze join columns and as hidden FHIR-extension IDs.
         */
        private Map<String, Object> ndrGoldRow(String patientRef, String dob,
                                               String district, String diagnosis) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("cpid", patientRef);                 // subject key → tokenised, never survives
            row.put("patient_ref", patientRef);          // raw bronze join key (not allowlisted → dropped)
            row.put("source_event_id", "evt-ndr-" + patientRef);  // bronze join key (dropped)
            row.put("name", PII_NAME);                   // free-text name
            row.put("healthId", PII_HEALTH_ID);
            row.put("nid", PII_NID);
            row.put("phone", PII_PHONE);
            row.put("extensionCpid", patientRef);        // hidden ID in a FHIR extension
            row.put("patientHealthId", PII_HEALTH_ID);   // hidden ID in a FHIR extension
            row.put("dob", dob);
            row.put("gender", "F");
            row.put("district", district);
            row.put("diagnosis", diagnosis);
            row.put("visit_date", "2026-07-03");
            return row;
        }
    }

    // ── Helpers ──

    private MockHttpServletRequestBuilder withHeaders(MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-Tenant-ID", TENANT_ID)
                .header("X-Pod-ID", "national")
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON);
    }

    /** Standard happy-path policy: ACTIVE, RESEARCH, deliberately allowlists identifiers. */
    private void upsertActiveResearchPolicy(String policyCode, int k) throws Exception {
        upsertPolicy(policyCode, "RESEARCH", "ACTIVE", k,
                List.of("cpid", "name", "healthId", "dob", "district",
                        "gender", "diagnosis", "visit_date"),
                Map.of("columns", Map.of(
                                "dob", "AGE_BAND_5",
                                "district", "DISTRICT",
                                "visit_date", "MONTH",
                                "diagnosis", "RARE_TO_OTHER"),
                        "quasiColumns", List.of("age_band", "district"),
                        "rareThreshold", 2));
    }

    private JsonNode upsertPolicy(String policyCode, String purpose, String policyStatus, int k,
                                  List<String> allowlist, Map<String, Object> rules) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("policy_code", policyCode);
        body.put("purpose", purpose);
        body.put("requester", "research-desk");
        body.put("retention_days", 365);
        body.put("reid_prohibited", true);
        body.put("k_threshold", k);
        body.put("quasi_identifier_rules", rules);
        body.put("allowlist_columns", allowlist);
        body.put("status", policyStatus);

        MvcResult result = mockMvc.perform(withHeaders(put("/internal/v1/deid/policies"))
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString());
    }

    private void createDataset(String datasetCode, String policyRef,
                               String secretRef, String zoneTarget) throws Exception {
        mockMvc.perform(withHeaders(post("/internal/v1/deid/datasets"))
                        .content(datasetBody(datasetCode, policyRef, secretRef, zoneTarget, "RESEARCH")))
                .andExpect(status().isCreated());
    }

    private String datasetBody(String datasetCode, String policyRef, String secretRef,
                               String zoneTarget, String purpose) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dataset_code", datasetCode);
        body.put("name", "Dataset " + datasetCode);
        body.put("purpose", purpose);
        body.put("zone_target", zoneTarget);
        if (policyRef != null) {
            body.put("policy_ref", policyRef);
        }
        body.put("secret_ref", secretRef);
        return MAPPER.writeValueAsString(body);
    }

    private JsonNode requestRun(String datasetCode, List<Map<String, Object>> rows) throws Exception {
        return MAPPER.readTree(requestRunRaw(datasetCode, rows));
    }

    private String requestRunRaw(String datasetCode, List<Map<String, Object>> rows) throws Exception {
        Map<String, Object> body = Map.of(
                "dataset_code", datasetCode,
                "requested_by", "analyst-1",
                "source_rows", rows);
        MvcResult result = mockMvc.perform(withHeaders(post("/internal/v1/deid/runs"))
                        .content(MAPPER.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private JsonNode getRun(String runId) throws Exception {
        MvcResult result = mockMvc.perform(withHeaders(get("/internal/v1/deid/runs/" + runId)))
                .andExpect(status().isOk())
                .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString());
    }

    private List<OutboxEventEntity> runEvents(String runId) {
        return outboxRepository.findAll().stream()
                .filter(e -> runId.equals(e.getAggregateId()))
                .toList();
    }

    /** 4 rows in one equivalence class (35-39 / Harare) + 1 singleton (suppressed). */
    private List<Map<String, Object>> happyRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        java.time.LocalDate now = java.time.LocalDate.now();
        int[] ages = {35, 36, 37, 38};      // all in the 35-39 band, whatever today is
        for (int i = 0; i < ages.length; i++) {
            Map<String, Object> row = piiRow("CPID-10" + i,
                    now.minusYears(ages[i]).minusMonths(1).toString());
            row.put("district", "Harare Province/Harare/Mbare Clinic");
            row.put("diagnosis", "TB");
            rows.add(row);
        }
        Map<String, Object> singleton = piiRow("CPID-109",
                now.minusYears(76).minusMonths(1).toString());   // 75-79 band, singleton class
        singleton.put("district", "Manicaland/Mutare/Sakubva Clinic");
        singleton.put("diagnosis", "RARE-SYNDROME");
        rows.add(singleton);
        return rows;
    }

    /** A CPID-keyed source row stuffed with direct identifiers. */
    private Map<String, Object> piiRow(String cpid, String dob) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cpid", cpid);
        row.put("name", PII_NAME);
        row.put("healthId", PII_HEALTH_ID);
        row.put("nid", PII_NID);
        row.put("phone", PII_PHONE);
        row.put("dob", dob);
        row.put("gender", "F");
        row.put("visit_date", "2026-07-03");
        return row;
    }

    private static void assertNoPii(String content) {
        assertThat(content).doesNotContain(PII_NAME);
        assertThat(content).doesNotContain(PII_HEALTH_ID);
        assertThat(content).doesNotContain(PII_NID);
        assertThat(content).doesNotContain(PII_PHONE);
        assertThat(content).doesNotContain("healthId");
        assertThat(content).doesNotContain("CPID-10");
    }
}
