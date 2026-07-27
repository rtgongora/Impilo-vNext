package zw.gov.mohcc.impilo.experience.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.time.Clock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The minted service token must survive a <strong>PATCH over a real socket</strong>.
 *
 * <p>Two defects hid behind each other here, and each made the other invisible:</p>
 * <ol>
 *   <li>service-originated calls carried no credential at all, so OAuth-enforcing downstreams
 *       401'd them into silent degradation (null ETA forever); and</li>
 *   <li>{@code serviceRestTemplate} was backed by {@code SimpleClientHttpRequestFactory}
 *       (HttpURLConnection), which throws {@code ProtocolException} on PATCH — so a perfectly
 *       minted token would be attached and the request would still die at the transport layer,
 *       before leaving the BFF, surfacing as a 502.</li>
 * </ol>
 *
 * <p>Every existing test in this area binds {@link org.springframework.test.web.client.MockRestServiceServer}
 * to the RestTemplate, which <em>replaces the request factory</em> — so it exercises the interceptor
 * but never the transport, and cannot observe defect 2 at all. This test therefore talks to a real
 * WireMock socket: it is the only shape in which "the Bearer is attached" and "the PATCH actually
 * completes" are asserted by the same request.</p>
 */
class ServiceTokenPatchLoopbackTest {

    private static final WireMockServer DOWNSTREAM = new WireMockServer(wireMockConfig().dynamicPort());

    @BeforeAll
    static void start() {
        DOWNSTREAM.start();
    }

    @AfterAll
    static void stop() {
        DOWNSTREAM.stop();
    }

    @Test
    void mintedServiceTokenReachesDownstreamOnARealPatch() {
        // A real provider minting against a mock Keycloak — not a stubbed provider, so the
        // credential under test is the one the interceptor would really attach in production.
        String tokenUri = "http://keycloak.test/realms/impilo/protocol/openid-connect/token";
        RestTemplate tokenClient = new RestTemplate();
        org.springframework.test.web.client.MockRestServiceServer keycloak =
                org.springframework.test.web.client.MockRestServiceServer.bindTo(tokenClient).build();
        keycloak.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(tokenUri))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"access_token\":\"patch-tok\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        ServiceTokenProvider provider = new ServiceTokenProvider(
                new ServiceTokenProperties(true, tokenUri, "impilo-backend", "s3cret", null),
                new ObjectMapper(), tokenClient, Clock.systemUTC());

        // The REAL bean, with its real transport factory. No MockRestServiceServer here.
        ServiceClientConfig config = new ServiceClientConfig();
        RestTemplate serviceRestTemplate =
                config.serviceRestTemplate(config.trustHeaderForwardingInterceptor(provider));

        DOWNSTREAM.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .patch(urlEqualTo("/v1/internal/vashandi/swaps/swap-1/decide"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"APPROVED\"}")));

        ResponseEntity<String> response = serviceRestTemplate.exchange(
                DOWNSTREAM.baseUrl() + "/v1/internal/vashandi/swaps/swap-1/decide",
                HttpMethod.PATCH,
                new HttpEntity<>("{\"decision\":\"APPROVE\"}", jsonHeaders()),
                String.class);

        // The PATCH completed over a real socket (defect 2) ...
        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"status\":\"APPROVED\"}", response.getBody());
        // ... and carried the minted credential when it got there (defect 1).
        DOWNSTREAM.verify(patchRequestedFor(urlEqualTo("/v1/internal/vashandi/swaps/swap-1/decide"))
                .withHeader("Authorization", equalTo("Bearer patch-tok")));
        keycloak.verify();
    }

    private static org.springframework.http.HttpHeaders jsonHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
