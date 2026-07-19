package zw.gov.mohcc.impilo.abis.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.abis.core.TemplateCrypto;
import zw.gov.mohcc.impilo.abis.core.BiometricModality;
import zw.gov.mohcc.impilo.abis.persistence.entity.TemplateEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * The ABIS adapter turns encrypted custody into real matching by delegating to the
 * engine — and is care-first: any engine failure fails closed (never a fabricated
 * match). Uses MockRestServiceServer to stand in for the matcher-engine.
 */
class BmeClientMatchingEngineTest {

    private static final String KEK = "101112131415161718191a1b1c1d1e1f000102030405060708090a0b0c0d0e0f";

    private TemplateCrypto crypto;
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private BmeClientMatchingEngine engine;

    @BeforeEach
    void setUp() {
        crypto = new TemplateCrypto(KEK);
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        engine = new BmeClientMatchingEngine(builder, "http://matcher-engine:9200", crypto);
    }

    private TemplateEntity enrolled(String subjectRef, byte[] plaintextTemplate) {
        TemplateEntity e = new TemplateEntity();
        e.setSubjectRef(subjectRef);
        e.setModality(BiometricModality.FINGERPRINT);
        e.setTemplateEncrypted(crypto.encrypt(plaintextTemplate)); // stored encrypted
        return e;
    }

    @Test
    @DisplayName("verify delegates decrypted templates to the engine and maps a MATCH")
    void verifyMatch() throws Exception {
        server.expect(requestTo("http://matcher-engine:9200/v1/engine/verify"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                // the ENROLLED template reaches the engine DECRYPTED (plaintext base64)
                .andExpect(jsonPath("$.enrolledTemplateBase64").value(
                        java.util.Base64.getEncoder().encodeToString("ENROLLED".getBytes())))
                .andExpect(jsonPath("$.modality").value("FINGERPRINT"))
                .andRespond(withSuccess(
                        "{\"result\":\"MATCH\",\"score\":420.0,\"confidence\":0.97,\"detail\":\"x\"}",
                        MediaType.APPLICATION_JSON));

        var d = engine.verify("PROBE".getBytes(), enrolled("subj-1", "ENROLLED".getBytes()),
                BiometricProbeContext.EMPTY);

        assertEquals("MATCH", d.result());
        assertEquals(0.97, d.confidence(), 1e-9);
        server.verify();
    }

    @Test
    @DisplayName("engine error → fail closed (UNAVAILABLE), never a fabricated match")
    void engineDownFailsClosed() {
        server.expect(requestTo("http://matcher-engine:9200/v1/engine/verify"))
                .andRespond(withServerError());

        var d = engine.verify("PROBE".getBytes(), enrolled("subj-1", "ENROLLED".getBytes()),
                BiometricProbeContext.EMPTY);

        assertEquals("UNAVAILABLE", d.result());
        assertEquals(0.0, d.confidence());
    }

    @Test
    @DisplayName("identify maps engine candidates (adjudication, never auto-merge); missing probe → none")
    void identifyMapsCandidates() {
        server.expect(requestTo("http://matcher-engine:9200/v1/engine/identify"))
                .andRespond(withSuccess(
                        "{\"candidates\":[{\"subjectRef\":\"subj-A\",\"score\":300,\"confidence\":0.9}]}",
                        MediaType.APPLICATION_JSON));

        var hits = engine.identify("PROBE".getBytes(),
                List.of(enrolled("subj-A", "T1".getBytes()), enrolled("subj-B", "T2".getBytes())),
                BiometricProbeContext.EMPTY);

        assertEquals(1, hits.size());
        assertEquals("subj-A", hits.get(0).subjectRef());
        assertEquals(0.9, hits.get(0).confidence(), 1e-9);

        assertTrue(engine.identify(new byte[0], List.of(), BiometricProbeContext.EMPTY).isEmpty());
    }
}
