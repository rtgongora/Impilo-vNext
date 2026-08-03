package zw.gov.mohcc.impilo.experience.controller;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The property under test is not "what commit is reported" — it is whether the reported commit is
 * ever presented as observed when it was not. Before this resolver, /health/version reported
 * shellCommit from a Helm-release-time environment value with no way for a caller to tell the
 * difference, so a shell rolled on its own kept naming the previous commit and read as fresh.
 */
class ShellCommitResolverTest {

    private ShellCommitResolver resolverPointedAt(String url) {
        ShellCommitResolver resolver = new ShellCommitResolver();
        ReflectionTestUtils.setField(resolver, "shellVersionUrl", url);
        return resolver;
    }

    @Test
    void reportsTheCommitTheShellItselfReturns() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/health/version", exchange -> {
            byte[] body = "{\"service\":\"one-ui-shell\",\"commit\":\"deadbeefcafe\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ShellCommitResolver resolver = resolverPointedAt(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/health/version");

            // The deploy-time value is deliberately a DIFFERENT commit: this is exactly the
            // post-targeted-roll situation, and the live answer must win.
            ShellCommitResolver.Resolution resolution = resolver.resolve("6af5001339e1d9ce");

            assertThat(resolution.commit()).isEqualTo("deadbeefcafe");
            assertThat(resolution.source()).isEqualTo(ShellCommitResolver.Source.LIVE);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void labelsTheDeployTimeValueUnverifiedWhenTheShellCannotBeReached() {
        // Port 1 is reserved and never listening, so this exercises the real failure path.
        ShellCommitResolver resolver = resolverPointedAt("http://127.0.0.1:1/api/health/version");

        ShellCommitResolver.Resolution resolution = resolver.resolve("6af5001339e1d9ce");

        // The value is still reported — losing it would be its own kind of dishonesty — but it
        // must never be marked LIVE, because nothing observed it.
        assertThat(resolution.commit()).isEqualTo("6af5001339e1d9ce");
        assertThat(resolution.source())
                .isEqualTo(ShellCommitResolver.Source.DEPLOY_MANIFEST_UNVERIFIED);
    }

    @Test
    void reportsUnknownRatherThanInventingACommit() {
        ShellCommitResolver resolver = resolverPointedAt("http://127.0.0.1:1/api/health/version");

        ShellCommitResolver.Resolution resolution = resolver.resolve("");

        // The old code fell back to the BFF's own commit here, which is how a shell-only roll
        // came to report the BFF's SHA as the shell's.
        assertThat(resolution.commit()).isEmpty();
        assertThat(resolution.source()).isEqualTo(ShellCommitResolver.Source.UNKNOWN);
    }

    @Test
    void speaksHttp11BecauseTheShellCannotSurviveAnH2cUpgrade() {
        ShellCommitResolver resolver = resolverPointedAt("http://127.0.0.1:1/api/health/version");
        HttpClient client = (HttpClient) ReflectionTestUtils.getField(resolver, "httpClient");

        // Java's default is HTTP_2, which attempts an h2c upgrade on plaintext. Measured against
        // the live estate, one-ui-shell:3000 fails that outright (http=000) while a Spring service
        // negotiates down to 1.1 cleanly — so this pin is load-bearing, not stylistic. A local
        // HttpServer-backed test cannot catch its removal, hence asserting the pin directly.
        assertThat(client).isNotNull();
        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }

    @Test
    void treatsANonOkShellResponseAsNoAnswer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/health/version", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            ShellCommitResolver resolver = resolverPointedAt(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/health/version");

            ShellCommitResolver.Resolution resolution = resolver.resolve("6af5001339e1d9ce");

            assertThat(resolution.source())
                    .isEqualTo(ShellCommitResolver.Source.DEPLOY_MANIFEST_UNVERIFIED);
        } finally {
            server.stop(0);
        }
    }
}
