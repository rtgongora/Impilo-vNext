package zw.gov.mohcc.impilo.inpatient.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureSpecimenEntity;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Pipeline §24 (interoperability) — the FHIR {@code Specimen} resource {@link
 * ButanoProcedureClient#writeSpecimen} authors. Proves the actual resource SHAPE, not just that
 * some object reaches a mock: status derivation (FHIR's own available/unsatisfactory/
 * entered-in-error vocabulary, not a passthrough of this schema's own status/adequacy strings),
 * and that optional blocks (collection, container, note) are omitted rather than emitted empty
 * when their source data is absent.
 */
class ButanoProcedureClientTest {

    private static final UUID TENANT = UUID.randomUUID();

    private RestTemplate rt;
    private MockRestServiceServer server;
    private ButanoProcedureClient client;

    @BeforeEach
    void setUp() {
        rt = new RestTemplate();
        server = MockRestServiceServer.bindTo(rt).build();
        client = new ButanoProcedureClient(rt, "http://butano-fhir", new ObjectMapper());
        TrustContextHolder.set(new TrustContext(TENANT, "actor-nurse", "STAFF", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private ProcedureSpecimenEntity specimen() {
        ProcedureSpecimenEntity s = new ProcedureSpecimenEntity();
        s.setSpecimenId(UUID.randomUUID());
        s.setTenantId(TENANT);
        s.setEpisodeId(UUID.randomUUID());
        s.setSpecimenLabel("Appendix");
        s.setSpecimenType("HISTOPATH");
        s.setStatus("COLLECTED");
        return s;
    }

    /** Parses the FHIR payload out of the envelope {@code write()} actually posts. */
    private JsonNode capturedPayload(String rawRequestBody) throws java.io.IOException {
        ObjectMapper om = new ObjectMapper();
        JsonNode envelope = om.readTree(rawRequestBody);
        assertThat(envelope.get("resourceType").asText()).isEqualTo("Specimen");
        return om.readTree(envelope.get("payload").asText());
    }

    @Test
    void aFullyCollectedSpecimenProducesACompleteFhirSpecimen() throws Exception {
        ProcedureSpecimenEntity s = specimen();
        s.setCollectedBy("nurse-1");
        s.setCollectedAt(OffsetDateTime.parse("2026-07-28T08:00:00Z"));
        s.setBodySite("Appendix");
        s.setContainerType("formalin jar");
        s.setOrosSpecimenId("OROS-SPEC-1");

        server.expect(requestTo("http://butano-fhir/internal/v1/fhir/resources"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(request -> {
                    JsonNode payload = capturedPayload(request.getBody().toString());
                    assertThat(payload.get("status").asText()).isEqualTo("available");
                    assertThat(payload.get("accessionIdentifier").get("value").asText()).isEqualTo("OROS-SPEC-1");
                    assertThat(payload.get("type").get("text").asText()).isEqualTo("HISTOPATH");
                    assertThat(payload.get("collection").get("collector").get("display").asText()).isEqualTo("nurse-1");
                    assertThat(payload.get("collection").get("bodySite").get("text").asText()).isEqualTo("Appendix");
                    assertThat(payload.get("container").get(0).get("type").get("text").asText()).isEqualTo("formalin jar");
                    return withSuccess("{\"resourceId\":\"specimen-xyz\"}", MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        String ref = client.writeSpecimen("CPID-1", s);

        assertThat(ref).isEqualTo("specimen-xyz");
        server.verify();
    }

    /** A specimen with no collection data yet omits the collection/container blocks entirely. */
    @Test
    void aNotYetCollectedSpecimenOmitsCollectionAndContainerBlocks() throws Exception {
        ProcedureSpecimenEntity s = specimen(); // no collectedBy/At, no containerType

        server.expect(requestTo("http://butano-fhir/internal/v1/fhir/resources"))
                .andRespond(request -> {
                    JsonNode payload = capturedPayload(request.getBody().toString());
                    assertThat(payload.has("collection")).isFalse();
                    assertThat(payload.has("container")).isFalse();
                    assertThat(payload.has("note")).isFalse();
                    return withSuccess("{\"resourceId\":\"specimen-abc\"}", MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        client.writeSpecimen("CPID-1", s);
        server.verify();
    }

    @Test
    void aRejectedSpecimenMapsToFhirsEnteredInErrorNotItsOwnRejectedString() throws Exception {
        ProcedureSpecimenEntity s = specimen();
        s.setStatus("REJECTED");

        server.expect(requestTo("http://butano-fhir/internal/v1/fhir/resources"))
                .andRespond(request -> {
                    JsonNode payload = capturedPayload(request.getBody().toString());
                    assertThat(payload.get("status").asText()).isEqualTo("entered-in-error");
                    return withSuccess("{\"resourceId\":\"specimen-rej\"}", MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        client.writeSpecimen("CPID-1", s);
        server.verify();
    }

    @Test
    void anInadequateSpecimenMapsToFhirsUnsatisfactoryNotItsOwnInadequateString() throws Exception {
        ProcedureSpecimenEntity s = specimen();
        s.setAdequacy("INADEQUATE");

        server.expect(requestTo("http://butano-fhir/internal/v1/fhir/resources"))
                .andRespond(request -> {
                    JsonNode payload = capturedPayload(request.getBody().toString());
                    assertThat(payload.get("status").asText()).isEqualTo("unsatisfactory");
                    return withSuccess("{\"resourceId\":\"specimen-inadq\"}", MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        client.writeSpecimen("CPID-1", s);
        server.verify();
    }

    /** Best-effort: a downstream failure resolves to null, never an exception the caller must catch. */
    @Test
    void aButanoOutageResolvesToNullNeverAnException() {
        server.expect(requestTo("http://butano-fhir/internal/v1/fhir/resources"))
                .andRespond(request -> { throw new java.io.IOException("connection refused"); });

        String ref = client.writeSpecimen("CPID-1", specimen());

        assertThat(ref).isNull();
    }

    @Test
    void labelConfirmationAndReceiptAppearAsAuditNotes() throws Exception {
        ProcedureSpecimenEntity s = specimen();
        s.setLabelConfirmedBy("nurse-2");
        s.setLabelConfirmedAt(OffsetDateTime.parse("2026-07-28T08:05:00Z"));
        s.setReceivedBy("lab-clerk-1");
        s.setReceivedAt(OffsetDateTime.parse("2026-07-28T09:00:00Z"));

        server.expect(requestTo("http://butano-fhir/internal/v1/fhir/resources"))
                .andRespond(request -> {
                    JsonNode payload = capturedPayload(request.getBody().toString());
                    assertThat(payload.get("note")).hasSize(2);
                    assertThat(payload.get("note").get(0).get("text").asText()).contains("nurse-2");
                    assertThat(payload.get("note").get(1).get("text").asText()).contains("lab-clerk-1");
                    return withSuccess("{\"resourceId\":\"specimen-notes\"}", MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        client.writeSpecimen("CPID-1", s);
        server.verify();
    }
}
