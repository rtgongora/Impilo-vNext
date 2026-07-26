package zw.gov.mohcc.impilo.experience;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Points every BFF downstream base URL at a closed port for the duration of a JVM test run.
 *
 * <p><b>Why this exists.</b> {@code ServiceClientConfig.ServiceEndpoints} defaults ~85 downstream
 * base URLs to {@code http://localhost:<dev port>} — PCT on 8088, OROS on 8089, inventory-service
 * on 8098, and so on. Nothing in the {@code test} profile overrode them, so a {@code @SpringBootTest}
 * did not talk to a fixture: it talked to <em>whatever happened to be listening on the developer's
 * loopback at that instant</em>. On a machine that also runs the preview estate — a service started
 * by hand, a {@code kubectl port-forward} that comes and goes, an nginx on 8089 — the same test
 * asserts against a different world on each run.</p>
 *
 * <p>That was the mechanism behind the experience-bff suite failing three different ways on three
 * identical runs. It was demonstrated by binding a stub on 127.0.0.1:8098 and re-running
 * {@code GoldenPathIntegrationTest}: {@code inventoryItems} flipped from green to
 * {@code Status expected:<502> but was:<200>} with no code change.</p>
 *
 * <p>The empty-200 honesty sweep did not introduce this — it exposed it. The old assertions
 * ("200 with a data array") were satisfied by the BFF's own fabricated empty payload whatever the
 * downstream did, so they were accidentally insensitive to the host. The honest assertions
 * ("502, because the downstream could not be reached") are only true if the downstream really is
 * unreachable, which until now was a matter of luck rather than configuration.</p>
 *
 * <p>Rewriting by name pattern rather than by an enumerated list is deliberate: a base URL added
 * for a new sovereign service is neutralised automatically instead of quietly reopening the hole.
 * Port 1 is privileged and never bound, so calls fail fast with connection-refused — the same
 * trick {@code application-test.yml} already uses for {@code KEYCLOAK_URL}.</p>
 *
 * <p>The pinned values are added as the highest-precedence property source at
 * environment-preparation time. A class that needs a real stub still wins, because
 * {@code @DynamicPropertySource} is applied later and also inserts first — that is how
 * {@link ExperienceBffSovereignWireMockSupport} and {@link ExperienceBffReportingWireMockSupport}
 * continue to redirect their handful of endpoints to WireMock.</p>
 */
public class ClosedLoopbackDownstreamsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** Privileged, never-bound port: connect() fails immediately rather than hanging. */
    static final String CLOSED_PORT_URL = "http://127.0.0.1:1";

    static final String PROPERTY_SOURCE_NAME = "closed-loopback-downstreams";

    private static final Pattern LOOPBACK_URL =
            Pattern.compile("^https?://(localhost|127\\.0\\.0\\.1|\\[::1])(:\\d+)?(/.*)?$");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> pinned = new LinkedHashMap<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                if (!isDownstreamUrl(name) || pinned.containsKey(name)) {
                    continue;
                }
                if (pointsAtLoopback(environment, name)) {
                    pinned.put(name, CLOSED_PORT_URL);
                }
            }
        }
        if (!pinned.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, pinned));
        }
    }

    /**
     * Only Impilo's own downstream endpoints. Spring's own {@code spring.*} URLs (Redis, Kafka,
     * the OAuth2 issuer) are left alone — those are configured per test class where they matter.
     */
    static boolean isDownstreamUrl(String name) {
        return name.startsWith("impilo.") && (name.endsWith("-url") || name.endsWith(".url"));
    }

    private static boolean pointsAtLoopback(ConfigurableEnvironment environment, String name) {
        String value;
        try {
            value = environment.getProperty(name);
        } catch (RuntimeException unresolvablePlaceholder) {
            return false;
        }
        return value != null && LOOPBACK_URL.matcher(value.trim()).matches();
    }

    @Override
    public int getOrder() {
        // After ConfigDataEnvironmentPostProcessor, so application.yml / application-test.yml
        // values are present and can be inspected.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
