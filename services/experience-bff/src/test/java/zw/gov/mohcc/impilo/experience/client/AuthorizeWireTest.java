package zw.gov.mohcc.impilo.experience.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The defect this pins was found on a live pod, and it is the most consequential kind in this
 * codebase: <strong>a check that could never succeed, hidden by failing closed.</strong>
 *
 * <p>This client sent the action it was asking about as HTTP/2 pseudo-headers, {@code :method}
 * and {@code :path}. Java rejects a colon-prefixed header name — {@code invalid header name:
 * ":method"} — so every call threw, and the old catch-all returned {@code false}. Fourteen
 * governance checks across the BFF have therefore always denied, and nothing distinguished that
 * from a policy decision.</p>
 *
 * <p>Sending them was doubly wrong: the PDP's servlet fallback would have evaluated the literal
 * {@code POST /v1/authorize}, which matches no rule and denies anyway.</p>
 */
class AuthorizeWireTest {

    private MockRestServiceServer server;
    private TshepoAuthzServiceClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        ServiceClientConfig.ServiceEndpoints endpoints =
                org.mockito.Mockito.mock(ServiceClientConfig.ServiceEndpoints.class);
        org.mockito.Mockito.when(endpoints.tshepoAuthzBaseUrl()).thenReturn("http://tshepo-authz:8081");
        client = new TshepoAuthzServiceClient(restTemplate, endpoints);
    }

    @Test
    @DisplayName("the action is sent as headers a real HTTP client will actually transmit")
    void sendsSendableHeaders() {
        server.expect(requestTo(containsString("/v1/authorize")))
                .andExpect(header(TrustHeaders.ORIGINAL_METHOD, "GET"))
                .andExpect(header(TrustHeaders.ORIGINAL_PATH, "/internal/v1/telemedicine-governed-read"))
                .andRespond(withSuccess("{\"verdict\":\"ALLOW\"}", MediaType.APPLICATION_JSON));

        assertThat(client.telemedicineRead().allowed()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("no colon-prefixed header name is sent from this client")
    void sendsNoPseudoHeaders() throws Exception {
        // The rejection happens in the transport, not in HttpHeaders -- `set(":method", …)`
        // accepts the name quite happily, which is precisely why the defect survived: nothing
        // failed until a real request went out, and by then the catch-all had turned it into a
        // denial. The live pod logged:
        //
        //   TSHEPO-AUTHZ: synthetic authorize failed
        //   path=/internal/v1/telemedicine-governed-read: invalid header name: ":method"
        //
        // MockRestServiceServer performs no real transport, so it cannot reproduce that. What is
        // verifiable here is that the client no longer constructs such a name at all.
        Path src = Path.of("src/main/java/zw/gov/mohcc/impilo/experience/client/"
                + "TshepoAuthzServiceClient.java");
        assertThat(Files.isRegularFile(src))
                .as("client source not found — this assertion would pass vacuously").isTrue();
        String body = Files.readString(src);

        assertThat(body)
                .as("a colon-prefixed header name is unsendable over HTTP/1.1 and throws at send time")
                .doesNotContain("headers.set(\":")
                .doesNotContain("set(\":method\"")
                .doesNotContain("set(\":path\"");
    }

    @Test
    @DisplayName("the PDP honours the alias, and prefers Envoy's pseudo-header over it")
    void producerHonoursTheAlias() throws Exception {
        // A consumer-side test alone would pass against a producer that ignores the header.
        Path producer = Path.of("../tshepo-authz-service/src/main/java/zw/gov/mohcc/impilo/"
                + "tshepo/authz/api/AuthorizeController.java");
        assertThat(Files.isRegularFile(producer))
                .as("producer not found at %s — this assertion would pass vacuously",
                        producer.toAbsolutePath())
                .isTrue();
        String src = Files.readString(producer);

        assertThat(src).contains("TrustHeaders.ORIGINAL_METHOD");
        assertThat(src).contains("TrustHeaders.ORIGINAL_PATH");

        // Envoy's own value must still win: when the PDP sits on the ext_authz path, the
        // pseudo-header is the trustworthy one and the alias is caller-supplied.
        int method = src.indexOf("String originalMethod = firstPresent(");
        assertThat(method).as("precedence helper is gone or renamed").isGreaterThan(-1);
        String block = src.substring(method, src.indexOf(");", method));
        assertThat(block.indexOf(":method")).isLessThan(block.indexOf("ORIGINAL_METHOD"));
    }

    @Test
    @DisplayName("the alias headers are stripped at the edge in EVERY strip list")
    void aliasesAreStrippedAtTheEdge() throws Exception {
        // These are decision inputs. A client able to set them would choose which resource its
        // own request is authorized against -- strictly worse than spoofing x-actor-id, because
        // it redirects the question rather than the answer.
        Path envoy = Path.of("../../deploy/helm/impilo-vnext/templates/envoy.yaml");
        assertThat(Files.isRegularFile(envoy))
                .as("envoy template not found at %s", envoy.toAbsolutePath()).isTrue();
        String chart = Files.readString(envoy);

        long actorStrips = chart.lines().filter(l -> l.strip().equals("- \"x-actor-id\"")).count();
        long pathStrips = chart.lines().filter(l -> l.strip().equals("- \"x-original-path\"")).count();
        long methodStrips = chart.lines().filter(l -> l.strip().equals("- \"x-original-method\"")).count();

        assertThat(actorStrips).as("no x-actor-id strip lists found — the anchor moved").isGreaterThan(0);
        assertThat(pathStrips)
                .as("x-original-path must be stripped everywhere x-actor-id is, or one route leaks")
                .isEqualTo(actorStrips);
        assertThat(methodStrips).isEqualTo(actorStrips);
    }
}
