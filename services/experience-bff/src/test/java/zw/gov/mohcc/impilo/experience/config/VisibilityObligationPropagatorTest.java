package zw.gov.mohcc.impilo.experience.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Forwarding the PDP visibility obligation on the BFF→service hop, and the gate that keeps it off.
 *
 * <p>The most important test here is the first one: the shipped configuration must forward nothing.
 * The pre-check found the deployed Envoy runs with no ext_authz filter and no
 * {@code request_headers_to_remove}, so every visibility header is client-forgeable — which makes the
 * BFF's non-forwarding the only thing between a forged category grant and pct's fail-closed
 * confidentiality guard. A default that silently flipped to on would be a privilege escalation.
 */
class VisibilityObligationPropagatorTest {

    private static final String ALL_ELEVEN_PRESENT = "SEXUAL_REPRODUCTIVE_HEALTH";

    private static HttpRequest outbound() {
        return new MockClientHttpRequest(HttpMethod.GET, URI.create("http://pct-service:8088/v1/x"));
    }

    private static MockHttpServletRequest inboundWithFullObligation() {
        MockHttpServletRequest inbound = new MockHttpServletRequest("GET", "/internal/v1/confidential/x");
        inbound.addHeader("x-obligations", "{\"visibilityProfile\":{\"aggregateOnly\":false}}");
        inbound.addHeader("x-visibility-tier", "FULL_IDENTIFIED_CLINICAL");
        inbound.addHeader("x-pii-access", "FULL");
        inbound.addHeader("x-clinical-access", "FULL");
        inbound.addHeader("x-aggregate-only", "false");
        inbound.addHeader("x-resource-sensitivity", "SPECIALLY_PROTECTED");
        inbound.addHeader("x-escalation-grant-id", "grant-1");
        inbound.addHeader("x-export-policy", "NONE");
        inbound.addHeader("x-suppress-fields", "nationalId");
        inbound.addHeader("x-drill-down-allowed", "true");
        inbound.addHeader("x-confidential-categories", ALL_ELEVEN_PRESENT);
        return inbound;
    }

    @Test
    @DisplayName("DISABLED is the shipped state and forwards NOTHING — enabling it is blocked on the edge fix")
    void disabledForwardsNothing() {
        VisibilityObligationPropagator propagator = new VisibilityObligationPropagator(false);
        HttpRequest outbound = outbound();

        propagator.propagate(inboundWithFullObligation(), outbound);

        assertThat(propagator.enabled()).isFalse();
        assertThat(outbound.getHeaders())
                .as("with no strip at the edge, forwarding would carry a forgeable "
                        + "x-confidential-categories to a fail-closed guard as a grant")
                .isEmpty();
    }

    @Test
    @DisplayName("ENABLED forwards all eleven headers together, because the parser overlays them field by field")
    void enabledForwardsAllEleven() {
        VisibilityObligationPropagator propagator = new VisibilityObligationPropagator(true);
        HttpRequest outbound = outbound();

        propagator.propagate(inboundWithFullObligation(), outbound);

        assertThat(outbound.getHeaders().keySet())
                .as("forwarding a subset hands a downstream a profile assembled from two different "
                        + "decisions — a partial grant nobody made")
                .containsExactlyInAnyOrderElementsOf(VisibilityObligationPropagator.VISIBILITY_HEADERS);
        assertThat(outbound.getHeaders().getFirst("x-confidential-categories"))
                .isEqualTo(ALL_ELEVEN_PRESENT);
    }

    @Test
    @DisplayName("a pre-set narrower obligation wins: forwarding is only-if-absent")
    void preSetObligationIsNotOverwritten() {
        VisibilityObligationPropagator propagator = new VisibilityObligationPropagator(true);
        HttpRequest outbound = outbound();
        outbound.getHeaders().set("x-confidential-categories", "NONE_GRANTED");

        propagator.propagate(inboundWithFullObligation(), outbound);

        assertThat(outbound.getHeaders().getFirst("x-confidential-categories"))
                .as("interceptors run after the client method built its headers, so a plain set would "
                        + "silently widen a deliberately narrowed obligation")
                .isEqualTo("NONE_GRANTED");
    }

    @Test
    @DisplayName("absent inbound headers are not forwarded as blanks")
    void absentHeadersAreNotInvented() {
        VisibilityObligationPropagator propagator = new VisibilityObligationPropagator(true);
        HttpRequest outbound = outbound();
        MockHttpServletRequest inbound = new MockHttpServletRequest("GET", "/internal/v1/confidential/x");
        inbound.addHeader("x-confidential-categories", "");

        propagator.propagate(inbound, outbound);

        assertThat(outbound.getHeaders())
                .as("a blank grant must read as absent, not as an empty allow-list the parser would "
                        + "treat as a present profile")
                .isEmpty();
    }

    @Test
    @DisplayName("every header the parser reads is in the forwarded set — a missing one is a partial grant")
    void theForwardedSetMatchesWhatTheParserReads() {
        assertThat(VisibilityObligationPropagator.VISIBILITY_HEADERS)
                .containsExactlyInAnyOrder(
                        "x-obligations",
                        "x-visibility-tier",
                        "x-pii-access",
                        "x-clinical-access",
                        "x-aggregate-only",
                        "x-resource-sensitivity",
                        "x-escalation-grant-id",
                        "x-export-policy",
                        "x-suppress-fields",
                        "x-drill-down-allowed",
                        "x-confidential-categories");
    }
}
