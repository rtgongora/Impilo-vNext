package zw.gov.mohcc.impilo.tshepo.authz.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.contracts.trust.DecisionEnvelopeClaims;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DecisionEnvelopeSignerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CORRELATION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String KEYS_URL = "http://keys:8184";
    private static final String SIGN_URL = KEYS_URL + "/v1/sign";

    private RestTemplate restTemplate;
    private MockRestServiceServer keysService;
    private AuthzProperties properties;
    private ObjectMapper objectMapper;
    private DecisionEnvelopeSigner signer;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        keysService = MockRestServiceServer.createServer(restTemplate);
        objectMapper = new ObjectMapper();
        properties = new AuthzProperties();
        properties.setKeysServiceUrl(KEYS_URL);
        properties.setDecisionEnvelopeEnabled(true);
        signer = new DecisionEnvelopeSigner(restTemplate, properties, objectMapper);
    }

    private Optional<String> sign() {
        return signer.sign(TENANT, "actor-1", "PROVIDER", CORRELATION,
                "GET", "/v1/patients/p1", "{\"maxScope\":\"read\"}");
    }

    @Test
    @DisplayName("off by default, so no deployment starts paying for envelopes nothing verifies")
    void disabledByDefault() {
        assertThat(new AuthzProperties().isDecisionEnvelopeEnabled()).isFalse();

        properties.setDecisionEnvelopeEnabled(false);
        assertThat(sign()).isEmpty();
        keysService.verify(); // no call was made at all
    }

    @Test
    @DisplayName("signs through keys-service, never by exporting a private key")
    void signsViaKeysService() {
        keysService.expect(requestTo(SIGN_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.jwsCompact").value(true))
                .andExpect(jsonPath("$.purpose").value("AUTHZ_DECISION"))
                .andExpect(jsonPath("$.tenantId").value(TENANT.toString()))
                .andRespond(withSuccess("{\"keyId\":\"k1\",\"algorithm\":\"EdDSA\",\"signature\":\"h.p.s\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(sign()).contains("h.p.s");
        keysService.verify();
        assertThat(signer.signedCount()).isEqualTo(1);
        assertThat(signer.failedCount()).isZero();
    }

    @Test
    @DisplayName("binds the decision to this actor, tenant, correlation, method and path")
    void payloadCarriesTheBinding() throws Exception {
        StringBuilder captured = new StringBuilder();
        keysService.expect(requestTo(SIGN_URL))
                .andExpect(request -> captured.append(request.getBody().toString()))
                .andRespond(withSuccess("{\"signature\":\"h.p.s\"}", MediaType.APPLICATION_JSON));

        sign();

        JsonNode body = objectMapper.readTree(captured.toString());
        JsonNode claims = objectMapper.readTree(body.get("payload").asText());

        assertThat(claims.get(DecisionEnvelopeClaims.CLAIM_ISSUER).asText())
                .isEqualTo(DecisionEnvelopeClaims.ISSUER);
        assertThat(claims.get(DecisionEnvelopeClaims.CLAIM_PURPOSE).asText())
                .isEqualTo(DecisionEnvelopeClaims.PURPOSE);
        assertThat(claims.get(DecisionEnvelopeClaims.CLAIM_DECISION).asText()).isEqualTo("ALLOW");
        assertThat(claims.get(DecisionEnvelopeClaims.CLAIM_SUBJECT).asText()).isEqualTo("actor-1");
        assertThat(claims.get(DecisionEnvelopeClaims.CLAIM_ACTOR_TYPE).asText()).isEqualTo("PROVIDER");
        assertThat(claims.get(DecisionEnvelopeClaims.CLAIM_TENANT_ID).asText()).isEqualTo(TENANT.toString());
        assertThat(claims.get(DecisionEnvelopeClaims.CLAIM_CORRELATION_ID).asText())
                .isEqualTo(CORRELATION.toString());
        assertThat(claims.get(DecisionEnvelopeClaims.CLAIM_METHOD).asText()).isEqualTo("GET");
        assertThat(claims.get(DecisionEnvelopeClaims.CLAIM_PATH).asText()).isEqualTo("/v1/patients/p1");
        assertThat(claims.get(DecisionEnvelopeClaims.CLAIM_OBLIGATIONS_DIGEST).asText())
                .isEqualTo(DecisionEnvelopeClaims.digest("{\"maxScope\":\"read\"}"));

        long exp = claims.get(DecisionEnvelopeClaims.CLAIM_EXPIRES_AT).asLong();
        long iat = claims.get(DecisionEnvelopeClaims.CLAIM_ISSUED_AT).asLong();
        assertThat(exp - iat).isEqualTo(properties.getDecisionEnvelopeTtlSeconds());
        assertThat(claims.get(DecisionEnvelopeClaims.CLAIM_JWT_ID).asText()).isNotBlank();
    }

    @Test
    @DisplayName("a keys-service outage allows without an envelope rather than denying the estate")
    void keysServiceFailureFailsOpenHere() {
        keysService.expect(requestTo(SIGN_URL)).andRespond(withServerError());

        assertThat(sign()).isEmpty();
        assertThat(signer.failedCount()).isEqualTo(1);
        assertThat(signer.signedCount()).isZero();
    }

    @Test
    @DisplayName("a 200 with no signature is a failure, not an envelope of empty string")
    void emptySignatureIsAFailure() {
        keysService.expect(requestTo(SIGN_URL))
                .andRespond(withSuccess("{\"keyId\":\"k1\",\"signature\":\"\"}", MediaType.APPLICATION_JSON));

        assertThat(sign()).isEmpty();
        assertThat(signer.failedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a purpose this keys-service does not know is refused, not signed with a general key")
    void unknownPurposeIsRefusedUpstream() {
        // What an older keys-service now does with AUTHZ_DECISION: 400, rather than quietly
        // substituting the GENERAL key and returning a signature that looks perfectly valid.
        keysService.expect(requestTo(SIGN_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"Unknown key purpose: AUTHZ_DECISION\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThat(sign()).isEmpty();
        assertThat(signer.failedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("no actor or no tenant means nothing to bind, so nothing is signed")
    void refusesToMintAnEnvelopeNamingNobody() {
        assertThat(signer.sign(null, "actor-1", "PROVIDER", CORRELATION, "GET", "/x", "{}")).isEmpty();
        assertThat(signer.sign(TENANT, null, "PROVIDER", CORRELATION, "GET", "/x", "{}")).isEmpty();
        assertThat(signer.sign(TENANT, "  ", "PROVIDER", CORRELATION, "GET", "/x", "{}")).isEmpty();
        keysService.verify(); // none of them reached keys-service
    }

    @Test
    @DisplayName("latency benchmark: the added hop is measured, not assumed")
    void measuresItsOwnSigningLatency() {
        int calls = 20;
        for (int i = 0; i < calls; i++) {
            keysService.expect(requestTo(SIGN_URL))
                    .andRespond(withSuccess("{\"signature\":\"h.p.s\"}", MediaType.APPLICATION_JSON));
        }
        for (int i = 0; i < calls; i++) {
            assertThat(sign()).isPresent();
        }

        assertThat(signer.signedCount()).isEqualTo(calls);
        // Against a mock transport this measures payload build + serialization only, which is
        // the part that is ours; the network hop is the deployment's to measure. The assertion
        // is a ceiling on our own overhead, deliberately loose enough not to flake on CI.
        assertThat(signer.averageSignMicros()).isLessThan(20_000L);
        System.out.printf("decision-envelope local overhead: %d µs/sign over %d signs%n",
                signer.averageSignMicros(), calls);
    }

    @Test
    @DisplayName("the digest commits to the exact obligations string, so widening them breaks it")
    void digestDiscriminates() {
        String narrow = "{\"maxScope\":\"read\"}";
        String widened = "{\"maxScope\":\"write\"}";

        assertThat(DecisionEnvelopeClaims.digest(narrow))
                .isNotEqualTo(DecisionEnvelopeClaims.digest(widened));
        assertThat(DecisionEnvelopeClaims.digest(narrow))
                .isEqualTo(DecisionEnvelopeClaims.digest(narrow));
        // Absent obligations digest to the same value as empty, so a verifier never meets a
        // null it has to decide what to do with.
        assertThat(DecisionEnvelopeClaims.digest(null)).isEqualTo(DecisionEnvelopeClaims.digest(""));
    }
}
