package zw.gov.mohcc.impilo.costa.config;

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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the interceptor refuses, not merely that the duty map says the right thing.
 *
 * <p>A map test and a wiring test fail for different reasons: the map can be perfect while the
 * interceptor never runs, and the interceptor can run while the map lets a citizen through. Both
 * are covered — this class is the enforcement half.</p>
 *
 * <p>No Spring context: this service has no H2 test profile, and a guard test that needs a database
 * to run is a guard test that stops running.</p>
 */
class MoneyWriteGuardInterceptorTest {

    private final MoneyWriteGuardInterceptor interceptor = new MoneyWriteGuardInterceptor();

    @AfterEach
    void clearContext() {
        TrustContextHolder.clear();
    }

    private void actingAs(String actorType) {
        if (actorType == null) {
            TrustContextHolder.clear();
            return;
        }
        TrustContextHolder.set(new TrustContext(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                "actor-1", actorType, "TREATMENT", null,
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    private void call(String method, String path) {
        var req = new MockHttpServletRequest(method, path);
        req.setRequestURI(path);
        interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
    }

    private void assertRefused(String path, String actorType) {
        actingAs(actorType);
        var ex = assertThrows(ResponseStatusException.class, () -> call("POST", path),
                path + " must refuse " + actorType);
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    private void assertAllowed(String path, String actorType) {
        actingAs(actorType);
        assertDoesNotThrow(() -> call("POST", path), path + " must admit " + actorType);
    }

    @Test
    @DisplayName("a CITIZEN cannot waive, write off, or approve — the exposure P0 named")
    void citizenIsRefusedOnMoneyDecisions() {
        assertRefused("/costa/v1/waivers", "CITIZEN");
        assertRefused("/internal/v1/finance/receivables/write-off", "CITIZEN");
        assertRefused("/costa/v1/bills/11111111-1111-4111-8111-111111111111/approve", "CITIZEN");
        assertRefused("/internal/v1/finance/reports/close-period", "CITIZEN");
    }

    @Test
    @DisplayName("a CITIZEN cannot raise or price a charge against themselves either")
    void citizenIsRefusedOnCaptureToo() {
        assertRefused("/costa/v1/bills/draft", "CITIZEN");
        assertRefused("/costa/v1/estimate", "CITIZEN");
    }

    @Test
    @DisplayName("a CAREGIVER acting for someone else is refused on the same paths")
    void caregiverIsRefused() {
        assertRefused("/costa/v1/waivers", "CAREGIVER");
        assertRefused("/costa/v1/bills/draft", "CAREGIVER");
    }

    @Test
    @DisplayName("a PROVIDER may capture at the bedside but may not decide money")
    void providerMayCaptureButNotDecide() {
        assertAllowed("/costa/v1/bills/draft", "PROVIDER");
        assertAllowed("/costa/v1/estimate", "PROVIDER");
        assertAllowed("/costa/v1/bills/abc/lines", "PROVIDER");

        assertRefused("/costa/v1/waivers", "PROVIDER");
        assertRefused("/costa/v1/bills/abc/finalize", "PROVIDER");
        assertRefused("/internal/v1/finance/budgets/managed", "PROVIDER");
    }

    @Test
    @DisplayName("an OPERATOR passes on back-office money work")
    void operatorPasses() {
        assertAllowed("/costa/v1/waivers", "OPERATOR");
        assertAllowed("/internal/v1/finance/receivables/write-off", "OPERATOR");
        assertAllowed("/costa/v1/bills/abc/finalize", "OPERATOR");
    }

    @Test
    @DisplayName("SYSTEM and SERVICE pass, so scheduled and service-to-service money work survives")
    void machineCallersPass() {
        assertAllowed("/internal/v1/finance/reports/close-period", "SYSTEM");
        assertAllowed("/costa/v1/bills/abc/approve", "SERVICE");
    }

    @Test
    @DisplayName("no trust context at all is refused, never waved through")
    void absentContextIsRefused() {
        assertRefused("/costa/v1/waivers", null);
    }

    @Test
    @DisplayName("reads are untouched and the v1.1 probe stays exempt")
    void readsAndProbeAreNotGated() {
        actingAs("CITIZEN");
        assertDoesNotThrow(() -> call("GET", "/costa/v1/bills"));
        assertDoesNotThrow(() -> call("POST", "/internal/v1/test-command"));
    }

    @Test
    @DisplayName("an endpoint nobody has classified yet is still gated")
    void unlistedWriteIsGated() {
        assertRefused("/costa/v1/some-endpoint-added-tomorrow", "CITIZEN");
        assertAllowed("/costa/v1/some-endpoint-added-tomorrow", "OPERATOR");
    }

    /**
     * Every other test here builds the interceptor itself, so all of them would still pass if
     * {@link MoneyWriteGuardConfig} were deleted and the guard never ran against a real request.
     * This one asserts the registration, which is the half that actually puts it on the path.
     */
    @Test
    @DisplayName("the interceptor is registered for every path — not merely instantiable")
    void interceptorIsActuallyRegistered() {
        var registry = new org.springframework.web.servlet.config.annotation.InterceptorRegistry();
        new MoneyWriteGuardConfig().addInterceptors(registry);

        var method = org.springframework.util.ReflectionUtils.findMethod(
                registry.getClass(), "getInterceptors");
        org.springframework.util.ReflectionUtils.makeAccessible(method);
        @SuppressWarnings("unchecked")
        var registered = (java.util.List<Object>)
                org.springframework.util.ReflectionUtils.invokeMethod(method, registry);

        boolean found = registered.stream().anyMatch(i ->
                i instanceof org.springframework.web.servlet.handler.MappedInterceptor mi
                        ? mi.getInterceptor() instanceof MoneyWriteGuardInterceptor
                        : i instanceof MoneyWriteGuardInterceptor);
        org.junit.jupiter.api.Assertions.assertTrue(found,
                "MoneyWriteGuardInterceptor is not registered; the money gate would never run. "
                        + "Registered: " + registered);
    }
}
