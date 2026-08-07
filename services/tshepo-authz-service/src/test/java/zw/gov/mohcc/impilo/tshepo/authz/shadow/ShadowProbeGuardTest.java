package zw.gov.mohcc.impilo.tshepo.authz.shadow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.tshepo.authz.core.PolicyEngine;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.ShadowDecisionLogEntity;
import zw.gov.mohcc.impilo.tshepo.authz.session.SessionAssuranceRouter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The three gates on the temporary P1 probe endpoint, in order.
 *
 * <p>Order is itself a control: the feature gate runs before any bearer is touched, and caller
 * authentication runs before any policy evaluation. A measurement API that processes a token
 * before deciding whether the caller may talk to it is a token-processing surface exposed to
 * anything in the cluster.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShadowProbeGuardTest {

    private static final String KEY = "a-high-entropy-probe-secret-value";

    @Mock PolicyEngine policyEngine;
    @Mock SessionAssuranceRouter sessionRouter;
    @Mock ShadowDecisionRecorder recorder;

    private ShadowProbeProperties props;

    @BeforeEach
    void setUp() {
        props = new ShadowProbeProperties();
        props.setEnabled(true);
        props.setProbeKey(KEY);
        props.setMaxConcurrent(4);
    }

    private ShadowAuthorizationController controller() {
        return new ShadowAuthorizationController(policyEngine, sessionRouter, recorder, props);
    }

    private ShadowEvaluationRequest req() {
        return new ShadowEvaluationRequest("GET", "/internal/v1/facilities", "ALLOW", "CITIZEN",
                Map.of("actorId", "a-1"), "a-user-bearer", ShadowDecisionLogEntity.MODE_ASYNC, null);
    }

    @Test
    void disabledMeansTheEndpointDoesNotExist() {
        props.setEnabled(false);
        ResponseEntity<Map<String, String>> res = controller().evaluate(req(), KEY);

        assertEquals(404, res.getStatusCode().value());
        // The point of gate 1: with shadowing off, no token is processed and no policy runs, so a
        // disabled estate exposes no token-processing surface at all.
        verifyNoInteractions(sessionRouter, policyEngine, recorder);
    }

    @Test
    void aCallerWithoutTheProbeKeyIsRefusedBeforeAnyTokenIsTouched() {
        ResponseEntity<Map<String, String>> res = controller().evaluate(req(), null);

        assertEquals(403, res.getStatusCode().value());
        verifyNoInteractions(sessionRouter, policyEngine);
    }

    @Test
    void aWrongProbeKeyIsRefused() {
        assertEquals(403, controller().evaluate(req(), "not-the-key").getStatusCode().value());
        verifyNoInteractions(sessionRouter, policyEngine);
    }

    @Test
    void aBlankConfiguredKeyRefusesEveryone() {
        // Fail closed: a deployment that forgot the secret must not accept all callers. The
        // dangerous failure is the one that looks like it is working.
        props.setProbeKey("");
        assertEquals(403, controller().evaluate(req(), "").getStatusCode().value());
        assertEquals(403, controller().evaluate(req(), "anything").getStatusCode().value());
        verifyNoInteractions(sessionRouter, policyEngine);
    }

    @Test
    void theUsersBearerIsNeverAcceptedAsProbeAuthentication() {
        // The trust boundary this protects: the user's token is EVIDENCE BEING EVALUATED, not
        // proof that the caller may invoke an internal measurement API.
        assertEquals(403, controller().evaluate(req(), "a-user-bearer").getStatusCode().value());
        verifyNoInteractions(sessionRouter, policyEngine);
    }

    @Test
    void exhaustedCapacityDropsTheProbeAndRecordsWhy() throws Exception {
        props.setMaxConcurrent(1);
        ShadowAuthorizationController c = controller();

        // Hold the single permit inside a slow evaluation, then probe concurrently.
        when(sessionRouter.validateSession(any())).thenAnswer(inv -> {
            Thread.sleep(300);
            throw new IllegalStateException("unresolvable — irrelevant to this test");
        });
        Thread holder = new Thread(() -> c.evaluate(req(), KEY));
        holder.start();
        Thread.sleep(80);

        ResponseEntity<Map<String, String>> res = c.evaluate(req(), KEY);
        holder.join();

        // Never an error to the caller — production traffic must not be delayed or failed.
        assertEquals(202, res.getStatusCode().value());
        // And never a silent loss: the drop is counted.
        verify(recorder, atLeastOnce()).record(argThat(row ->
                ShadowDecisionLogEntity.OUTCOME_DROPPED_CAPACITY.equals(row.getProbeOutcome())));
    }

    @Test
    void theProbeKeyIsNeverPlacedInTheEvaluationRecord() {
        // Belt and braces alongside the entity-shape test: the key must not reach persistence.
        props.setEnabled(true);
        when(sessionRouter.validateSession(any())).thenThrow(new IllegalStateException("no session"));
        controller().evaluate(req(), KEY);

        verify(recorder).record(argThat(row ->
                row.getShadowRoles() == null || !row.getShadowRoles().contains(KEY)));
    }
}
