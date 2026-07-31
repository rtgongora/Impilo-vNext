package zw.gov.mohcc.impilo.experience.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BFF-side measurement of the visibility tightening — and the two properties that make it a
 * measurement rather than a change.
 */
class VisibilityPropagationShadowReporterTest {

    private static final String SIGNAL = "experience.visibility.shadow_restrict";

    private VisibilityPropagationShadowReporter reporter;
    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void setUp() {
        reporter = new VisibilityPropagationShadowReporter(new ObjectMapper());
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        logger = context.getLogger(VisibilityPropagationShadowReporter.class);
        appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private static HttpRequest outboundTo(String uri) {
        return new MockClientHttpRequest(HttpMethod.GET, URI.create(uri));
    }

    private List<String> signals() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains(SIGNAL))
                .toList();
    }

    @Test
    @DisplayName("an inbound request carrying no obligation is silent — nothing would change")
    void noProfileIsSilent() {
        MockHttpServletRequest inbound = new MockHttpServletRequest("GET", "/internal/v1/maternity/x");

        reporter.report(inbound, outboundTo("http://pct-service:8088/v1/x"));

        assertThat(signals())
                .as("a downstream already sees a null profile, so there is nothing to report")
                .isEmpty();
    }

    @Test
    @DisplayName("a profile that restricts nothing is silent — a line per unaffected call buries the signal")
    void unrestrictiveProfileIsSilent() {
        MockHttpServletRequest inbound = new MockHttpServletRequest("GET", "/internal/v1/maternity/x");
        inbound.addHeader("x-visibility-tier", "FULL_IDENTIFIED_CLINICAL");
        inbound.addHeader("x-clinical-access", "FULL");
        inbound.addHeader("x-confidential-categories", "SEXUAL_REPRODUCTIVE_HEALTH");
        inbound.addHeader("x-drill-down-allowed", "true");

        reporter.report(inbound, outboundTo("http://pct-service:8088/v1/x"));

        assertThat(signals()).isEmpty();
    }

    @Test
    @DisplayName("the absent confidential-category grant is reported — it is what withholds protected content")
    void absentCategoryGrantIsReported() {
        MockHttpServletRequest inbound = new MockHttpServletRequest("GET", "/internal/v1/maternity/x");
        inbound.addHeader("x-visibility-tier", "FULL_IDENTIFIED_CLINICAL");
        inbound.addHeader("x-clinical-access", "FULL");

        reporter.report(inbound, outboundTo("http://pct-service:8088/v1/x"));

        assertThat(signals()).hasSize(1);
        assertThat(signals().get(0))
                .contains("confidentialCategoriesAbsent")
                .contains("route=/internal/v1/maternity/x")
                .contains("downstream=pct-service");
    }

    @Test
    @DisplayName("aggregate-only and a restrictive clinical access are both named, because they tighten different guards")
    void restrictingAxesAreNamedIndividually() {
        MockHttpServletRequest inbound = new MockHttpServletRequest("GET", "/internal/v1/reports/x");
        inbound.addHeader("x-aggregate-only", "true");
        inbound.addHeader("x-clinical-access", "SUMMARY_ONLY");
        inbound.addHeader("x-confidential-categories", "*");

        reporter.report(inbound, outboundTo("http://reporting-service:8176/v1/x"));

        assertThat(signals()).hasSize(1);
        assertThat(signals().get(0))
                .as("AggregateVisibilityGuard and ClinicalVisibilityGuard are permissive on a null "
                        + "profile, so each is a separate route that forwarding would tighten")
                .contains("aggregateOnly")
                .contains("clinicalAccess=SUMMARY_ONLY");
        assertThat(signals().get(0)).doesNotContain("confidentialCategoriesAbsent");
    }

    @Test
    @DisplayName("the nested visibilityProfile inside x-obligations is read, not just the flat headers")
    void obligationsJsonIsRead() {
        MockHttpServletRequest inbound = new MockHttpServletRequest("GET", "/internal/v1/maternity/x");
        inbound.addHeader("x-obligations",
                "{\"visibilityProfile\":{\"aggregateOnly\":true,\"confidentialCategories\":[\"HIV\"]}}");

        reporter.report(inbound, outboundTo("http://pct-service:8088/v1/x"));

        assertThat(signals()).hasSize(1);
        assertThat(signals().get(0)).contains("aggregateOnly");
    }

    @Test
    @DisplayName("a malformed obligations blob is not allowed to fail the request it is observing")
    void malformedObligationsDoNotThrow() {
        MockHttpServletRequest inbound = new MockHttpServletRequest("GET", "/internal/v1/maternity/x");
        inbound.addHeader("x-obligations", "{not json");

        reporter.report(inbound, outboundTo("http://pct-service:8088/v1/x"));
        // Reaching this line at all is the assertion: a measurement must never break a live call.
        assertThat(signals()).isEmpty();
    }

    @Test
    @DisplayName("the reporter cannot mutate the outbound request — it observes only")
    void reporterDoesNotTouchTheOutboundRequest() {
        MockHttpServletRequest inbound = new MockHttpServletRequest("GET", "/internal/v1/maternity/x");
        inbound.addHeader("x-aggregate-only", "true");
        HttpRequest outbound = outboundTo("http://pct-service:8088/v1/x");

        reporter.report(inbound, outbound);

        assertThat(outbound.getHeaders())
                .as("step 1 is a measurement; the propagation is step 2 and is separately gated")
                .isEmpty();
    }
}
