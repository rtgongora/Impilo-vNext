package zw.gov.mohcc.impilo.tshepo.offline.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Client-side contract test for the keys-service signing call — the offline end of the
 * wire that caused G059. Pins the exact request: {@code POST /v1/sign} with the aligned
 * body {@code {tenantId,payload,jwsCompact,purpose}}, and that the client reads
 * {@code signature} from the response. The keys-service side of the same contract is
 * runtime-proven by {@code SigningRuntimeProofIT}; together they prove both ends match.
 */
class KeysServiceClientTest {

    private MockRestServiceServer server;
    private KeysServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://keys-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeysServiceClient(builder.build());
    }

    @Test
    void signPayload_postsAlignedContract_andReturnsSignature() {
        UUID tenantId = UUID.randomUUID();

        server.expect(requestTo("http://keys-service/v1/sign"))
                .andExpect(method(POST))
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.payload").value("{\"cap\":\"x\"}"))
                .andExpect(jsonPath("$.jwsCompact").value(true))
                .andExpect(jsonPath("$.purpose").value("OFFLINE_CAPABILITY"))
                .andRespond(withSuccess(
                        "{\"keyId\":\"kid-1\",\"algorithm\":\"Ed25519\",\"signature\":\"jws.compact.value\"}",
                        APPLICATION_JSON));

        String jws = client.signPayload(tenantId, "{\"cap\":\"x\"}", "OFFLINE_CAPABILITY");

        assertThat(jws).isEqualTo("jws.compact.value");
        server.verify();
    }
}
