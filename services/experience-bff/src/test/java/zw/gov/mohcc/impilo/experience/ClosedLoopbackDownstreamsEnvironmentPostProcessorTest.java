package zw.gov.mohcc.impilo.experience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static zw.gov.mohcc.impilo.experience.ClosedLoopbackDownstreamsEnvironmentPostProcessor.CLOSED_PORT_URL;

/**
 * The invariant this pins: in a test JVM, no BFF downstream may resolve to a port another
 * process on the host could be listening on. Without it, an integration test asserting
 * "the downstream is unreachable" is asserting a fact about the developer's machine.
 */
class ClosedLoopbackDownstreamsEnvironmentPostProcessorTest {

    private final ClosedLoopbackDownstreamsEnvironmentPostProcessor processor =
            new ClosedLoopbackDownstreamsEnvironmentPostProcessor();

    @Test
    @DisplayName("a downstream aimed at a live dev port is repointed at the closed port")
    void repointsLoopbackDownstreams() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("impilo.services.inventory-base-url", "http://localhost:8098")
                .withProperty("impilo.services.oros-base-url", "http://localhost:8089")
                .withProperty("impilo.services.fhir-base-url", "http://localhost:8090/fhir")
                .withProperty("impilo.services.orthanc-dicomweb-url", "http://127.0.0.1:8042/dicom-web");

        processor.postProcessEnvironment(environment, null);

        assertEquals(CLOSED_PORT_URL, environment.getProperty("impilo.services.inventory-base-url"));
        assertEquals(CLOSED_PORT_URL, environment.getProperty("impilo.services.oros-base-url"));
        assertEquals(CLOSED_PORT_URL, environment.getProperty("impilo.services.fhir-base-url"),
                "a path suffix does not make the host any less shared");
        assertEquals(CLOSED_PORT_URL, environment.getProperty("impilo.services.orthanc-dicomweb-url"));
    }

    @Test
    @DisplayName("nested downstream blocks are covered, not just impilo.services")
    void repointsNestedDownstreamBlocks() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("impilo.services.extra.observability-base-url", "http://localhost:8211")
                .withProperty("impilo.experience.one-ui-shell-base-url", "http://localhost:3000")
                .withProperty("impilo.services.learning.base-url", "http://localhost:8235");

        processor.postProcessEnvironment(environment, null);

        assertEquals(CLOSED_PORT_URL, environment.getProperty("impilo.services.extra.observability-base-url"));
        assertEquals(CLOSED_PORT_URL, environment.getProperty("impilo.experience.one-ui-shell-base-url"));
        assertEquals(CLOSED_PORT_URL, environment.getProperty("impilo.services.learning.base-url"));
    }

    @Test
    @DisplayName("a downstream already aimed at a test fixture is left alone")
    void leavesNonLoopbackAndNonImpiloPropertiesAlone() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("impilo.services.pct-base-url", "http://wiremock.example:34567")
                .withProperty("impilo.services.tuso-base-url", "not-a-url")
                .withProperty("spring.data.redis.host", "localhost")
                .withProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri", "http://localhost:8080/realms/impilo");

        processor.postProcessEnvironment(environment, null);

        assertEquals("http://wiremock.example:34567", environment.getProperty("impilo.services.pct-base-url"));
        assertEquals("not-a-url", environment.getProperty("impilo.services.tuso-base-url"));
        assertEquals("localhost", environment.getProperty("spring.data.redis.host"));
        assertEquals("http://localhost:8080/realms/impilo",
                environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"),
                "Spring's own configuration is owned by the test classes that set it");
    }

    @Test
    @DisplayName("only Impilo URL properties are eligible for repointing")
    void namePatternIsRestrictedToImpiloUrls() {
        assertTrue(ClosedLoopbackDownstreamsEnvironmentPostProcessor
                .isDownstreamUrl("impilo.services.vito-base-url"));
        assertTrue(ClosedLoopbackDownstreamsEnvironmentPostProcessor
                .isDownstreamUrl("impilo.services.learning.base-url"));
        assertFalse(ClosedLoopbackDownstreamsEnvironmentPostProcessor
                .isDownstreamUrl("impilo.security.allow-anonymous"));
        assertFalse(ClosedLoopbackDownstreamsEnvironmentPostProcessor
                .isDownstreamUrl("spring.data.redis.url"));
    }
}
