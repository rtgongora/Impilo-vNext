package zw.gov.mohcc.impilo.tshepo.authz.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.tshepo.authz.core.PolicyEngine;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.service.IdentityIntrospectionClient;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionAssuranceRouter;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionInfo;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The SYSTEM purpose of use may only be asserted by a workload identity established from a
 * validated token.
 *
 * <h2>What this closes</h2>
 *
 * <p>{@code SYSTEM} is the one purpose that reaches a clinical resource with no policy rule and no
 * consent evaluation: {@code PolicyEngine} short-circuits Step 4 for it in <em>both</em> branches
 * (no rules, and no matching rule), and {@code requiresConsent} returns false for it. It was read
 * straight from {@code x-purpose-of-use} with nothing checking who may assert it, and Envoy leaves
 * that header client-supplied by design.</p>
 *
 * <p>Measured on the live preview estate 2026-08-07: the header alone turned a 403
 * DENY/{@code NO_ALLOW_RULE} into a 200 ALLOW carrying {@code piiAccess=FULL} and
 * {@code clinicalAccess=FULL}, with an invented actor id and no bearer token.</p>
 *
 * <p>Failing closed is safe here and that was measured too: across 1,952 live decisions, zero
 * carried purpose SYSTEM, so no existing caller depends on it.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SYSTEM purpose requires a validated workload identity")
class SystemPurposeRequiresWorkloadIdentityTest {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final String CLINICAL_PATH = "/v1/patients/cpid-12345";

    @Mock private PolicyEngine policyEngine;
    @Mock private SessionAssuranceRouter sessionRouter;
    @Mock private IdentityIntrospectionClient introspectionClient;
    @Mock private HttpServletRequest request;

    private AuthorizeController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthorizeController(
                policyEngine, sessionRouter, new ObjectMapper(), introspectionClient);
        // recordPrePolicyDenial returns the refusal the controller hands back. An unstubbed mock
        // would return null, the controller would answer with a null body, and every assertion on
        // errorCode() would NPE — the suite would be exercising a degraded path rather than the
        // guard. Answer with the real deny it is contracted to produce.
        lenient().when(policyEngine.recordPrePolicyDenial(
                        any(AuthzInternalRequest.class), anyString(), anyString()))
                .thenAnswer(inv -> AuthzResponse.deny(inv.getArgument(1), inv.getArgument(2), 0));
    }

    // ════════════════════════════════════════════════════════════════════
    // The hole, closed
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("SYSTEM from a bare header with no token is REFUSED, and never reaches the engine")
    void systemPurpose_withNoToken_isRefused() {
        stubHeaders("SYSTEM", null);

        ResponseEntity<AuthzResponse> response = controller.authorize(request);

        assertThat(response.getStatusCode().value())
                .as("THE HOLE: a client-declared SYSTEM purpose must not be honoured")
                .isEqualTo(403);
        assertThat(response.getBody().errorCode()).isEqualTo("PURPOSE_NOT_PERMITTED");

        // The decisive pair. PolicyEngine short-circuits Step 4 for SYSTEM, so the request must
        // never reach evaluate() — refusing afterwards would be refusing after the bypass. But the
        // refusal must still be RECORDED, or the control works invisibly and a detection query on
        // purpose_of_use='SYSTEM' falls silent once this guard ships.
        verify(policyEngine, never()).evaluate(any(AuthzInternalRequest.class));
        verify(policyEngine).recordPrePolicyDenial(
                any(AuthzInternalRequest.class), eq("PURPOSE_NOT_PERMITTED"), anyString());
    }

    @Test
    @DisplayName("SYSTEM claimed via x-actor-type alone is REFUSED — the client cannot vouch for itself")
    void systemPurpose_withClientClaimedActorType_isRefused() {
        // The caller asserts BOTH the purpose and the workload actor type, with no token behind
        // either. An actor-supplied field consulted ABOUT that actor is an oracle, not evidence.
        stubHeaders("SYSTEM", null, "SERVICE");

        ResponseEntity<AuthzResponse> response = controller.authorize(request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().errorCode()).isEqualTo("PURPOSE_NOT_PERMITTED");
        verify(policyEngine, never()).evaluate(any(AuthzInternalRequest.class));
        verify(policyEngine).recordPrePolicyDenial(
                any(AuthzInternalRequest.class), eq("PURPOSE_NOT_PERMITTED"), anyString());
    }

    @Test
    @DisplayName("SYSTEM from an authenticated HUMAN session is REFUSED — this is the escalation path")
    void systemPurpose_withHumanSession_isRefused() {
        stubHeaders("SYSTEM", "Bearer human-token");
        when(sessionRouter.validateSession(anyString()))
                .thenReturn(session("PROVIDER"));

        ResponseEntity<AuthzResponse> response = controller.authorize(request);

        assertThat(response.getStatusCode().value())
                .as("a valid token for a person must not unlock the workload purpose")
                .isEqualTo(403);
        assertThat(response.getBody().errorCode()).isEqualTo("PURPOSE_NOT_PERMITTED");
        verify(policyEngine, never()).evaluate(any(AuthzInternalRequest.class));
        verify(policyEngine).recordPrePolicyDenial(
                any(AuthzInternalRequest.class), eq("PURPOSE_NOT_PERMITTED"), anyString());
    }

    // ════════════════════════════════════════════════════════════════════
    // Still open to the callers it exists for
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("SYSTEM from a validated SERVICE session is admitted to the engine")
    void systemPurpose_withWorkloadSession_isAdmitted() {
        stubHeaders("SYSTEM", "Bearer workload-token");
        when(sessionRouter.validateSession(anyString())).thenReturn(session("SERVICE"));
        when(introspectionClient.introspect(any())).thenReturn(DutyContext.absent());
        when(policyEngine.evaluate(any(AuthzInternalRequest.class)))
                .thenReturn(AuthzResponse.allow(null, 0, java.util.Map.of()));

        ResponseEntity<AuthzResponse> response = controller.authorize(request);

        assertThat(response.getBody().verdict()).isEqualTo(Verdict.ALLOW);
        verify(policyEngine).evaluate(any(AuthzInternalRequest.class));
    }

    @Test
    @DisplayName("SYSTEM from a real s2s token — no sub, no actor type, SERVICE role — is admitted")
    void systemPurpose_withServiceRoleOnlyToken_isAdmitted() {
        // The actual shape of a service-to-service token here: no sub, no actor-type claim, the
        // workload identity carried as a ROLE. Gating on actor type alone refused every legitimate
        // s2s caller — caught by the two pre-existing AuthorizeControllerTest cases.
        stubHeaders("SYSTEM", "Bearer service-token", "SERVICE");
        when(sessionRouter.validateSession(anyString()))
                .thenReturn(new SessionInfo(null, null, List.of("SERVICE"), null, 0, "s", "iss"));
        when(introspectionClient.introspect(any())).thenReturn(DutyContext.absent());
        when(policyEngine.evaluate(any(AuthzInternalRequest.class)))
                .thenReturn(AuthzResponse.allow(null, 0, java.util.Map.of()));

        controller.authorize(request);

        verify(policyEngine).evaluate(any(AuthzInternalRequest.class));
    }

    @Test
    @DisplayName("negative control: an ordinary TREATMENT purpose is unaffected by the guard")
    void treatmentPurpose_isUnaffected() {
        stubHeaders("TREATMENT", null);
        when(introspectionClient.introspect(any())).thenReturn(DutyContext.absent());
        when(policyEngine.evaluate(any(AuthzInternalRequest.class)))
                .thenReturn(AuthzResponse.deny("NO_ALLOW_RULE", "no rule", 0));

        controller.authorize(request);

        // Reaches the engine and is judged on its merits — the guard must not become a blanket
        // refusal, or it would pass for the wrong reason.
        verify(policyEngine).evaluate(any(AuthzInternalRequest.class));
    }

    @Test
    @DisplayName("case and whitespace do not evade the guard")
    void systemPurpose_lowercaseAndPadded_isRefused() {
        stubHeaders("  system  ", null);

        assertThat(controller.authorize(request).getStatusCode().value()).isEqualTo(403);
        verify(policyEngine, never()).evaluate(any(AuthzInternalRequest.class));
    }

    // ════════════════════════════════════════════════════════════════════
    // Fixtures
    // ════════════════════════════════════════════════════════════════════

    private void stubHeaders(String purpose, String authorization) {
        stubHeaders(purpose, authorization, "OPERATOR");
    }

    private void stubHeaders(String purpose, String authorization, String actorType) {
        lenient().when(request.getHeader(anyString())).thenReturn(null);
        lenient().when(request.getRequestURI()).thenReturn("/v1/authorize");
        lenient().when(request.getHeader("x-tenant-id")).thenReturn(TENANT);
        lenient().when(request.getHeader("x-actor-id")).thenReturn("probe-actor");
        lenient().when(request.getHeader("x-actor-type")).thenReturn(actorType);
        lenient().when(request.getHeader("x-purpose-of-use")).thenReturn(purpose);
        lenient().when(request.getHeader("x-original-path")).thenReturn(CLINICAL_PATH);
        lenient().when(request.getHeader("x-original-method")).thenReturn("GET");
        lenient().when(request.getHeader("authorization")).thenReturn(authorization);
    }

    private static SessionInfo session(String actorType) {
        return new SessionInfo("session-subject", actorType, List.of(), UUID.fromString(TENANT),
                2, "sid-1", "https://impilo.mohcc.gov.zw/realms/impilo");
    }
}
