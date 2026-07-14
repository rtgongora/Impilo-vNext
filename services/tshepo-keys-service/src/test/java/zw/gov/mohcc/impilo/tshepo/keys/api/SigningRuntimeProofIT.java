package zw.gov.mohcc.impilo.tshepo.keys.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.tshepo.keys.api.dto.SignPayloadRequest;
import zw.gov.mohcc.impilo.tshepo.keys.api.dto.SignPayloadResponse;
import zw.gov.mohcc.impilo.tshepo.keys.core.Ed25519SigningService;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.SigningKeyEntity;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.repository.SigningKeyRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runtime proof for the keys-service signing contract (G059) + purpose-scoped key
 * threading (fix-a), against a REAL Postgres.
 *
 * <p>This is the gold-standard proof that the broken cross-service contract is fixed:
 * it boots the full keys-service context, provisions an OFFLINE_CAPABILITY key, calls
 * the REAL {@code POST /v1/sign} endpoint (the one tshepo-offline now targets) with the
 * aligned DTO {@code {tenantId,payload,jwsCompact,purpose}}, and then <b>cryptographically
 * verifies the returned JWS against the live JWKS</b>. It also proves the fail-closed
 * path: a sensitive purpose with no provisioned key must NOT sign.</p>
 *
 * <p>Mocks could never catch G059 (the 404) — only a real HTTP round-trip against the
 * real controller + DB proves the endpoint exists, accepts the DTO, signs with the
 * purpose key, and that the kid resolves in the JWKS. Harness-driven (CLI/CI Postgres
 * via {@code -Dit.pg.url}); see {@code scripts/runtime-proof/tshepo-keys-signing.sh}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "it.pg.url", matches = ".+")
class SigningRuntimeProofIT {

    // Profile "it" loads ONLY the production application.yml (there is no
    // application-it.yml), i.e. real Postgres + Flyway + PostgreSQL dialect + the
    // production SecurityConfig (lazy issuer-uri JwtDecoder, so no Keycloak fetch at
    // startup). This deliberately avoids the H2 unit profile ("test") the golden
    // suites use. The datasource points at the real Postgres supplied via -Dit.pg.url.
    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("it.pg.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("it.pg.user"));
        registry.add("spring.datasource.password", () -> System.getProperty("it.pg.pass"));
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Ed25519SigningService signingService;
    @Autowired private SigningKeyRepository signingKeyRepository;

    /** Provision an ACTIVE key bound to the given purpose for a tenant. */
    private SigningKeyEntity provisionKey(UUID tenantId, String purpose) {
        SigningKeyEntity key = signingService.generateKeyPair(tenantId);
        key.setPurpose(purpose);
        return signingKeyRepository.save(key);
    }

    @Test
    void signWithPurposeKey_returnsJwsThatVerifiesAgainstJwks() throws Exception {
        UUID tenantId = UUID.randomUUID();
        SigningKeyEntity provisioned = provisionKey(tenantId, "OFFLINE_CAPABILITY");

        String body = objectMapper.writeValueAsString(
                new SignPayloadRequest(tenantId, "{\"cap\":\"offline-read\"}", true, "OFFLINE_CAPABILITY"));

        String responseJson = mockMvc.perform(post("/v1/sign")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        SignPayloadResponse response = objectMapper.readValue(responseJson, SignPayloadResponse.class);

        // The signing key used must be the purpose-scoped key we provisioned (not a GENERAL key).
        assertThat(response.keyId()).isEqualTo(provisioned.getKeyId());
        assertThat(response.signature()).isNotBlank();

        // Cryptographically verify the JWS against the live JWKS: kid must resolve and the
        // Ed25519 signature must validate. This is the real end-to-end signing proof.
        JWSObject jws = JWSObject.parse(response.signature());
        assertThat(jws.getHeader().getKeyID()).isEqualTo(provisioned.getKeyId());

        String jwksJson = mockMvc.perform(get("/v1/keys/jwks"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        OctetKeyPair jwk = (OctetKeyPair) JWKSet.parse(jwksJson).getKeyByKeyId(jws.getHeader().getKeyID());
        assertThat(jwk).as("signing kid must be published in the JWKS").isNotNull();
        assertThat(jws.verify(new Ed25519Verifier(jwk.toPublicJWK())))
                .as("JWS produced by /v1/sign must verify against the JWKS")
                .isTrue();
    }

    @Test
    void signWithSensitivePurposeAndNoKey_failsClosed() throws Exception {
        UUID tenantId = UUID.randomUUID(); // no PERMIT key provisioned for this tenant

        String body = objectMapper.writeValueAsString(
                new SignPayloadRequest(tenantId, "{\"x\":1}", true, "PERMIT"));

        // getActiveKeyForPurpose throws (fail-closed) ⇒ the request must NOT succeed.
        mockMvc.perform(post("/v1/sign")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is5xxServerError());
    }
}
