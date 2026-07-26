package zw.gov.mohcc.impilo.experience.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Provider tests against a mock HTTP token endpoint — the Keycloak call is real
 * form-encoded HTTP through the RestTemplate, NOT a mocked client. The prior failure
 * mode of this whole area was tests that mocked the seam away.
 */
class ServiceTokenProviderTest {

    private static final String TOKEN_URI = "http://keycloak.test/realms/impilo/protocol/openid-connect/token";

    private RestTemplate tokenClient;
    private MockRestServiceServer server;
    private MutableClock clock;
    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void setUp() {
        tokenClient = new RestTemplate();
        server = MockRestServiceServer.bindTo(tokenClient).build();
        clock = new MutableClock(Instant.parse("2026-07-26T12:00:00Z"));
        logCapture = new ListAppender<>();
        logCapture.start();
        ((Logger) LoggerFactory.getLogger(ServiceTokenProvider.class)).addAppender(logCapture);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(ServiceTokenProvider.class)).detachAppender(logCapture);
    }

    private ServiceTokenProvider provider(String secret) {
        return new ServiceTokenProvider(
                new ServiceTokenProperties(true, TOKEN_URI, "impilo-backend", secret, null),
                new ObjectMapper(), tokenClient, clock);
    }

    /** The literal client_credentials form Keycloak will receive. */
    @Test
    void mintsTokenWithLiteralFormEncodedClientCredentialsRequest() {
        server.expect(once(), requestTo(TOKEN_URI))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("grant_type=client_credentials&client_id=impilo-backend&client_secret=s3cret"))
                .andRespond(withSuccess("{\"access_token\":\"tok-1\",\"expires_in\":300}", MediaType.APPLICATION_JSON));

        assertEquals(Optional.of("tok-1"), provider("s3cret").getAccessToken());
        server.verify();
    }

    /** Cached until ~30s before expiry; one HTTP mint serves repeated calls, then refreshes. */
    @Test
    void cachesTokenUntilThirtySecondsBeforeExpiryThenRefreshes() {
        server.expect(once(), requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"tok-1\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"tok-2\",\"expires_in\":300}", MediaType.APPLICATION_JSON));

        ServiceTokenProvider provider = provider("s3cret");
        assertEquals(Optional.of("tok-1"), provider.getAccessToken());
        assertEquals(Optional.of("tok-1"), provider.getAccessToken()); // no second HTTP call

        clock.advance(Duration.ofSeconds(269)); // still inside expiry-30s window
        assertEquals(Optional.of("tok-1"), provider.getAccessToken());

        clock.advance(Duration.ofSeconds(2)); // now past the 270s refresh point
        assertEquals(Optional.of("tok-2"), provider.getAccessToken());
        server.verify();
    }

    /**
     * Honest failure: mint failure yields empty + SERVICE_TOKEN_MINT_FAILED WARN, the failure
     * is remembered no longer than 30s, then the provider genuinely retries.
     */
    @Test
    void mintFailureIsHonestAndNeverCachedLongerThanThirtySeconds() {
        server.expect(once(), requestTo(TOKEN_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(once(), requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"tok-after-recovery\",\"expires_in\":300}",
                        MediaType.APPLICATION_JSON));

        ServiceTokenProvider provider = provider("s3cret");
        assertEquals(Optional.empty(), provider.getAccessToken());
        assertTrue(warnLogged(ServiceTokenProvider.MINT_FAILED_MARKER),
                "mint failure must log the SERVICE_TOKEN_MINT_FAILED marker at WARN");

        // Inside the 30s backoff: no second HTTP call (the recovery expectation stays unconsumed).
        clock.advance(Duration.ofSeconds(10));
        assertEquals(Optional.empty(), provider.getAccessToken());

        // Past the 30s backoff: the provider retries for real and recovers.
        clock.advance(Duration.ofSeconds(21));
        assertEquals(Optional.of("tok-after-recovery"), provider.getAccessToken());
        server.verify();
    }

    /** Preview posture: secret absent → no HTTP call at all, empty result, honest WARN. */
    @Test
    void absentSecretYieldsEmptyWithoutAnyHttpCallAndWarns() {
        assertEquals(Optional.empty(), provider("").getAccessToken());
        server.verify(); // zero expectations declared — any HTTP call would have failed
        assertTrue(warnLogged(ServiceTokenProvider.MINT_FAILED_MARKER));
    }

    @Test
    void disabledProviderYieldsEmptyWithoutHttpOrWarn() {
        ServiceTokenProvider provider = new ServiceTokenProvider(
                new ServiceTokenProperties(false, TOKEN_URI, "impilo-backend", "s3cret", null),
                new ObjectMapper(), tokenClient, clock);
        assertEquals(Optional.empty(), provider.getAccessToken());
        server.verify();
        assertEquals(0, logCapture.list.size());
    }

    /** Configured scope must reach Keycloak in the form body. */
    @Test
    void scopeIsIncludedInTheFormWhenConfigured() {
        server.expect(once(), requestTo(TOKEN_URI))
                .andExpect(content().string(
                        "grant_type=client_credentials&client_id=impilo-backend&client_secret=s3cret&scope=impilo-s2s"))
                .andRespond(withSuccess("{\"access_token\":\"tok-1\",\"expires_in\":300}", MediaType.APPLICATION_JSON));

        ServiceTokenProvider provider = new ServiceTokenProvider(
                new ServiceTokenProperties(true, TOKEN_URI, "impilo-backend", "s3cret", "impilo-s2s"),
                new ObjectMapper(), tokenClient, clock);
        assertEquals(Optional.of("tok-1"), provider.getAccessToken());
        server.verify();
    }

    private boolean warnLogged(String marker) {
        return logCapture.list.stream()
                .anyMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN
                        && e.getFormattedMessage().contains(marker));
    }

    /** Deterministic, manually advanced clock. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
