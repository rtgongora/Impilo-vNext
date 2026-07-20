package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/**
 * W2c — the real key-custody resolver resolves references against tshepo-keys,
 * caches per tenant::ref, scopes by request tenant, and fails closed.
 */
class TshepoKeysSecretResolverTest {

    private static final String BASE = "http://tshepo-keys.test:8184";
    // Deterministic tenant UUID for the canonical string tenant "moh-zw".
    private static final String TENANT_UUID =
            java.util.UUID.nameUUIDFromBytes("impilo-tenant:moh-zw".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString();

    private void bindTenant() {
        RequestContextHolder.set(RequestContext.of(
                "moh-zw", "national", "req-1", "corr-1", null, null, null));
    }

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    @DisplayName("resolves the secret value from GET /v1/secrets/{ref}?tenantId=<uuid>")
    void resolvesFromTshepoKeys() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        server.expect(requestTo(BASE + "/v1/secrets/deid-ds-a?tenantId=" + TENANT_UUID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"secretRef\":\"deid-ds-a\",\"value\":\"managed-key-material-a\"}",
                        MediaType.APPLICATION_JSON));

        TshepoKeysSecretResolver resolver = new TshepoKeysSecretResolver(BASE, rt);
        bindTenant();

        assertThat(resolver.resolve("deid-ds-a")).isEqualTo("managed-key-material-a");
        server.verify();
    }

    @Test
    @DisplayName("caches per tenant::ref — a second resolve makes no second HTTP call")
    void cachesPerRef() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        server.expect(org.springframework.test.web.client.ExpectedCount.once(),
                        requestTo(BASE + "/v1/secrets/deid-ds-a?tenantId=" + TENANT_UUID))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"value\":\"managed-key-material-a\"}", MediaType.APPLICATION_JSON));

        TshepoKeysSecretResolver resolver = new TshepoKeysSecretResolver(BASE, rt);
        bindTenant();

        assertThat(resolver.resolve("deid-ds-a")).isEqualTo("managed-key-material-a");
        assertThat(resolver.resolve("deid-ds-a")).isEqualTo("managed-key-material-a");
        server.verify();   // exactly one upstream call
    }

    @Test
    @DisplayName("fails closed when tshepo-keys returns 404 for an unknown reference")
    void failsClosedOnNotFound() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        server.expect(requestTo(BASE + "/v1/secrets/missing?tenantId=" + TENANT_UUID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        TshepoKeysSecretResolver resolver = new TshepoKeysSecretResolver(BASE, rt);
        bindTenant();

        assertThatThrownBy(() -> resolver.resolve("missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not available");
    }

    @Test
    @DisplayName("fails closed when tshepo-keys is unreachable / errors")
    void failsClosedOnTransportError() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        server.expect(requestTo(BASE + "/v1/secrets/deid-ds-a?tenantId=" + TENANT_UUID))
                .andRespond(withServerError());

        TshepoKeysSecretResolver resolver = new TshepoKeysSecretResolver(BASE, rt);
        bindTenant();

        assertThatThrownBy(() -> resolver.resolve("deid-ds-a"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("fails closed when a body is returned with a blank value")
    void failsClosedOnBlankValue() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);
        server.expect(requestTo(BASE + "/v1/secrets/deid-ds-a?tenantId=" + TENANT_UUID))
                .andRespond(withSuccess("{\"value\":\"\"}", MediaType.APPLICATION_JSON));

        TshepoKeysSecretResolver resolver = new TshepoKeysSecretResolver(BASE, rt);
        bindTenant();

        assertThatThrownBy(() -> resolver.resolve("deid-ds-a"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("requires a bound request tenant — resolving without one fails closed")
    void requiresBoundTenant() {
        TshepoKeysSecretResolver resolver = new TshepoKeysSecretResolver(BASE, new RestTemplate());
        // no RequestContext bound
        assertThatThrownBy(() -> resolver.resolve("deid-ds-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    @DisplayName("blank secret reference fails closed")
    void blankRefFailsClosed() {
        TshepoKeysSecretResolver resolver = new TshepoKeysSecretResolver(BASE, new RestTemplate());
        bindTenant();
        assertThatThrownBy(() -> resolver.resolve("  "))
                .isInstanceOf(IllegalStateException.class);
    }
}
