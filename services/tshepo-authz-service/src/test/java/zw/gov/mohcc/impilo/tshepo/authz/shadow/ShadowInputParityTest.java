package zw.gov.mohcc.impilo.tshepo.authz.shadow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.tshepo.authz.core.PolicyEngine;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.ShadowDecisionLogEntity;
import zw.gov.mohcc.impilo.tshepo.authz.service.IdentityIntrospectionClient;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionAssuranceRouter;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionInfo;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A shadow measurement is only worth reading if the shadow and production evaluations differ in
 * <b>exactly one</b> input.
 *
 * <p>The class doc on {@code ShadowAuthorizationController} claims that property. These tests are
 * what make the claim checkable, because the failure mode is silent and expensive: an input the
 * shadow path nulls out does not produce an error, it produces a <em>delta</em> — and a delta is
 * precisely the thing the whole exercise is measuring. A shadow that passes {@code resourceType =
 * null} would report PERMIT_TO_DENY across every resource-scoped rule in the estate and read as a
 * catastrophic finding rather than as a bug in the probe.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShadowInputParityTest {

    private static final String KEY = "a-high-entropy-probe-secret-value";

    @Mock PolicyEngine policyEngine;
    @Mock SessionAssuranceRouter sessionRouter;
    @Mock IdentityIntrospectionClient introspectionClient;
    @Mock ShadowDecisionRecorder recorder;

    private ShadowProbeProperties props;

    @BeforeEach
    void setUp() {
        props = new ShadowProbeProperties();
        props.setEnabled(true);
        props.setProbeKey(KEY);
        props.setMaxConcurrent(4);
        when(sessionRouter.validateSession(any())).thenReturn(new SessionInfo(
                "actor-1", "PROVIDER", List.of("CLINICIAN"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"), 2, "sid-1", "iss"));
        when(policyEngine.evaluate(any())).thenReturn(AuthzResponse.allow(null, 0, null));
        when(introspectionClient.introspect(any())).thenReturn(DutyContext.absent());
    }

    private ShadowAuthorizationController controller() {
        return new ShadowAuthorizationController(
                policyEngine, sessionRouter, introspectionClient, recorder, props);
    }

    private AuthzInternalRequest evaluateAndCapture(Map<String, String> trustContext) {
        controller().evaluate(new ShadowEvaluationRequest(
                "POST", "/internal/v1/pct/encounters/9f1c7a6e-0000-4000-8000-000000000001/observations",
                "ALLOW", "CITIZEN", trustContext, "a-user-bearer",
                ShadowDecisionLogEntity.MODE_ASYNC, null), KEY);
        ArgumentCaptor<AuthzInternalRequest> captor = ArgumentCaptor.forClass(AuthzInternalRequest.class);
        verify(policyEngine).evaluate(captor.capture());
        return captor.getValue();
    }

    /**
     * The caller does not get to choose which rule its own measurement is scored against.
     *
     * <p>Production derives action/resourceType/resourceId from method+path inside
     * {@code AuthorizeController}. If the shadow path read them from the request body instead, a
     * BFF bug — or anything that could reach the endpoint — could aim the measurement at a
     * different rule set than the one production used.
     */
    @Test
    void resourceCoordinatesAreDerivedFromTheRequestNotSuppliedByTheCaller() {
        AuthzInternalRequest captured = evaluateAndCapture(Map.of(
                "actorId", "actor-1",
                "resourceType", "not-the-real-resource-type",
                "action", "NOT_THE_REAL_ACTION"));

        assertEquals(AuthzInternalRequest.deriveResourceType(
                        "/internal/v1/pct/encounters/9f1c7a6e-0000-4000-8000-000000000001/observations"),
                captured.resourceType(),
                "resourceType must be derived exactly as AuthorizeController derives it");
        assertEquals(AuthzInternalRequest.deriveAction("POST",
                        "/internal/v1/pct/encounters/9f1c7a6e-0000-4000-8000-000000000001/observations"),
                captured.action(),
                "action must be derived, not taken from the caller");
        assertNotNull(captured.resourceType(),
                "a null resourceType misses every resource-scoped rule and fakes PERMIT_TO_DENY");
    }

    /** Production introspects the duty token on every request; a shadow that did not would differ twice. */
    @Test
    void theDutyContextProductionWouldSeeIsCarriedIntoTheShadowEvaluation() {
        DutyContext proven = new DutyContext(true, true, "WORK_CONTEXT", "actor-1",
                "fac-1", null, null, null, null, null, "PROV-1", "NURSE", "assign-1",
                null, "CLINICAL_CARE", "IDENTIFIED", "ctx-1", null);
        when(introspectionClient.introspect("a-duty-token")).thenReturn(proven);

        AuthzInternalRequest captured = evaluateAndCapture(Map.of(
                "actorId", "actor-1", "workContextToken", "a-duty-token"));

        assertSame(proven, captured.dutyContext(),
                "the introspected duty context must reach PolicyEngine, as it does in production");
    }

    /**
     * The header analogue of the role law.
     *
     * <p>{@code x-provider-id} is client-supplied — PolicyEngine echoes back whatever arrived, and
     * the server-side resolver that would populate it is citizen-only. Letting it select
     * MY_PROFESSIONAL would reproduce, with a header, the exact defect the projection refuses with
     * a role: a caller choosing its own capacity.</p>
     */
    @Test
    void aClientAssertedProviderIdNeverSelectsAPersona() {
        ArgumentCaptor<ShadowDecisionLogEntity> row = ArgumentCaptor.forClass(ShadowDecisionLogEntity.class);

        evaluateAndCapture(Map.of("actorId", "actor-1", "providerId", "PROV-ASSERTED-BY-CALLER"));
        verify(recorder).record(row.capture());

        assertEquals(PrincipalProjection.PERSONA_MY_LIFE, row.getValue().getActivePersona(),
                "an unproven provider id must not promote the persona");
        assertEquals("CITIZEN", row.getValue().getLegacyActorType(),
                "nor the legacy actor type the 352 existing rules match on");
    }

    /** A proven duty token, by contrast, is exactly what may set the persona. */
    @Test
    void aProvenDutyModeBecomesThePersonaVerbatim() {
        when(introspectionClient.introspect("a-duty-token")).thenReturn(new DutyContext(
                true, true, "WORK_CONTEXT", "actor-1", "fac-1", null, null, null, null, null,
                "PROV-1", "NURSE", "assign-1", null, "CLINICAL_CARE", "IDENTIFIED", "ctx-1", null));
        ArgumentCaptor<ShadowDecisionLogEntity> row = ArgumentCaptor.forClass(ShadowDecisionLogEntity.class);

        evaluateAndCapture(Map.of("actorId", "actor-1", "workContextToken", "a-duty-token"));
        verify(recorder).record(row.capture());

        assertEquals("CLINICAL_CARE", row.getValue().getActivePersona());
        assertEquals("PROVIDER", row.getValue().getLegacyActorType());
    }
}
