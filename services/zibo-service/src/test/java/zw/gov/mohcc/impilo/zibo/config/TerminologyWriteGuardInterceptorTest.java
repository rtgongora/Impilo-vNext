package zw.gov.mohcc.impilo.zibo.config;

import jakarta.servlet.DispatcherType;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the gate refuses — and, just as importantly, proves it lets the right caller through.
 *
 * <p>A guard that refuses everything is indistinguishable from a working one if you only test the
 * refusals. Every refusal case here has a matching success case.</p>
 */
class TerminologyWriteGuardInterceptorTest {

    private final TerminologyWriteGuardInterceptor interceptor = new TerminologyWriteGuardInterceptor();

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private static void presentActorType(String actorType) {
        TrustContextHolder.set(new TrustContext(
                UUID.randomUUID(), "actor-1", actorType, "GOVERNANCE",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setRequestURI(path);
        return req;
    }

    private boolean preHandle(MockHttpServletRequest req) {
        return interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
    }

    // --- refusals -------------------------------------------------------------------------------

    @Test
    @DisplayName("publishing national terminology as a PROVIDER is refused")
    void providerCannotPublish() {
        presentActorType("PROVIDER");

        assertThatThrownBy(() -> preHandle(request("POST", "/v1/artifacts/abc/publish")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                // The refusal must name the act and both sides of the decision, so the first
                // legitimate steward refused can act on it rather than guess. ActorTypeGuard
                // capitalises the description when it builds the message.
                .hasMessageContaining("Publishing a terminology artifact")
                .hasMessageContaining("[OPERATOR, SERVICE, SYSTEM]")
                .hasMessageContaining("PROVIDER");
    }

    @Test
    @DisplayName("a caller presenting no trust context at all is refused, not waved through")
    void absentContextIsRefused() {
        TrustContextHolder.clear();

        assertThatThrownBy(() -> preHandle(request("POST", "/v1/artifacts/abc/retire")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a CITIZEN cannot rebuild the concept index")
    void citizenCannotRebuildTheIndex() {
        presentActorType("CITIZEN");

        assertThatThrownBy(() -> preHandle(request("POST", "/v1/concepts/rebuild")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("an endpoint nobody mapped a duty for is still refused")
    void unlistedWriteIsRefused() {
        presentActorType("PROVIDER");

        assertThatThrownBy(() -> preHandle(request("DELETE", "/v1/something/new")))
                .isInstanceOf(ResponseStatusException.class);
    }

    // --- successes: the half that proves the gate is a gate and not a wall ----------------------

    @Test
    @DisplayName("an OPERATOR publishes successfully — the gate opens for the steward it is for")
    void operatorCanPublish() {
        presentActorType("OPERATOR");

        assertThatCode(() -> preHandle(request("POST", "/v1/artifacts/abc/publish")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a SERVICE workload token can import content and rebuild indexes")
    void serviceCanImportAndRebuild() {
        presentActorType("SERVICE");

        assertThatCode(() -> preHandle(request("POST", "/v1/import/fhir-bundle")))
                .doesNotThrowAnyException();
        assertThatCode(() -> preHandle(request("POST", "/v1/concepts/rebuild")))
                .doesNotThrowAnyException();
        assertThatCode(() -> preHandle(request("POST", "/v1/map/rebuild")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a clinician still validates a coding during care")
    void providerCanStillValidateTerminology() {
        presentActorType("PROVIDER");

        // If these ever start throwing, terminology validation has been switched off mid-consultation
        // and it will look like a hardening rather than an outage.
        for (String clinical : new String[] {
                "/api/v1/terminology/validate",
                "/v1/validate/coding",
                "/v1/validate/resource",
                "/v1/map",
                "/internal/v1/confidentiality/classify"}) {
            assertThatCode(() -> preHandle(request("POST", clinical)))
                    .as(clinical + " must remain reachable from the clinical path")
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("reads are untouched — a CITIZEN may look up a code")
    void readsAreNotGated() {
        presentActorType("CITIZEN");

        assertThatCode(() -> preHandle(request("GET", "/v1/CodeSystem/$lookup")))
                .doesNotThrowAnyException();
        assertThatCode(() -> preHandle(request("GET", "/v1/artifacts/abc")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the ERROR dispatch is not re-gated, so a downstream fault is not rewritten as 403")
    void errorDispatchIsNotRegated() {
        // By the ERROR dispatch, TrustContextFilter has cleared its thread-local in a finally block.
        // Re-running the gate would refuse, and a genuine 500 would come back as a bare 403 with the
        // real fault invisible. costa hit exactly this live.
        TrustContextHolder.clear();
        MockHttpServletRequest req = request("POST", "/v1/artifacts/abc/publish");
        req.setDispatcherType(DispatcherType.ERROR);

        assertThatCode(() -> preHandle(req)).doesNotThrowAnyException();
    }
}
