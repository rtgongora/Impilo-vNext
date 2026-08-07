package zw.gov.mohcc.impilo.tshepo.authz.shadow;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.ShadowDecisionLogEntity;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The security invariants, held by tests rather than by intention.
 *
 * <p>The realistic way a token escapes is not a log line someone wrote on purpose. It is a DTO
 * landing inside an exception message, a debug dump, or a column somebody added later because it
 * seemed useful. These tests make each of those a build failure.</p>
 */
class ShadowBearerContainmentTest {

    private static final String SECRET = "eyJhbGciOiJSUzI1NiJ9.SUPER_SECRET_TOKEN_VALUE.sig";

    private ShadowEvaluationRequest requestCarryingTheToken() {
        return new ShadowEvaluationRequest(
                "GET", "/internal/v1/facilities", "ALLOW", "CITIZEN",
                Map.of("actorId", "a-1", "purposeOfUse", "TREATMENT"),
                SECRET, ShadowDecisionLogEntity.MODE_ASYNC, null);
    }

    @Test
    void toStringNeverRevealsTheBearer() {
        String rendered = requestCarryingTheToken().toString();
        assertFalse(rendered.contains(SECRET), "toString leaked the bearer: " + rendered);
        assertTrue(rendered.contains("<redacted>"));
    }

    @Test
    void stringInterpolationOfTheRequestNeverRevealsTheBearer() {
        // How it actually happens: log.warn("failed for {}", request)
        String viaFormat = String.format("failed for %s", requestCarryingTheToken());
        assertFalse(viaFormat.contains(SECRET));
    }

    @Test
    void anExceptionCarryingTheRequestNeverRevealsTheBearer() {
        // A wrapped exception is the other route out — into an error response or an APM trace.
        RuntimeException ex = new RuntimeException("shadow failed: " + requestCarryingTheToken());
        assertFalse(ex.getMessage().contains(SECRET));
    }

    @Test
    void theShadowLogHasNoFieldCapableOfHoldingACredential() {
        // Structural, not stylistic. If someone adds a token/bearer/secret column later, this fails
        // — which is the point: the table's inability to store a credential is the control.
        for (Field f : ShadowDecisionLogEntity.class.getDeclaredFields()) {
            // Instance fields only. A `static final String OUTCOME_BEARER_UNRESOLVED` is an outcome
            // NAME — it holds no per-request data and cannot carry a credential. Scoping to
            // instance state is what the invariant actually means; the first draft of this test
            // failed on that constant, which is the test being wrong, not the entity.
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            String n = f.getName().toLowerCase();
            assertFalse(n.contains("bearer") || n.contains("token") || n.contains("secret")
                            || n.contains("credential") || n.contains("authorization"),
                    "shadow_decision_log gained a field that could hold a credential: " + f.getName());
        }
    }

    @Test
    void theEnvelopeRecordsOnlyDerivedAuthority() {
        // Roles are derived facts and may be stored; the thing they were derived FROM may not.
        ShadowDecisionLogEntity row = new ShadowDecisionLogEntity();
        row.setShadowRoles("CLINICIAN,CITIZEN");
        assertEquals("CLINICIAN,CITIZEN", row.getShadowRoles());
        assertFalse(row.getShadowRoles().contains(SECRET));
    }

    @Test
    void permitToDenyIsClassifiedAheadOfAClassificationChange() {
        // The delta that would break live traffic must never be masked by a persona change that
        // happens to accompany it.
        assertEquals(ShadowDecisionLogEntity.DELTA_PERMIT_TO_DENY,
                ShadowAuthorizationController.classifyDelta("ALLOW", "DENY", "CITIZEN", "PROVIDER"));
    }

    @Test
    void dormantRulesWakingUpIsClassifiedAsDenyToPermit() {
        assertEquals(ShadowDecisionLogEntity.DELTA_DENY_TO_PERMIT,
                ShadowAuthorizationController.classifyDelta("DENY", "ALLOW", "CITIZEN", "CITIZEN"));
    }

    @Test
    void aPersonaChangeAloneIsStillReported() {
        assertEquals(ShadowDecisionLogEntity.DELTA_ACTOR_TYPE_CHANGE,
                ShadowAuthorizationController.classifyDelta("ALLOW", "ALLOW", "CITIZEN", "PROVIDER"));
    }

    @Test
    void agreementIsNotADelta() {
        assertEquals(ShadowDecisionLogEntity.DELTA_NONE,
                ShadowAuthorizationController.classifyDelta("ALLOW", "ALLOW", "CITIZEN", "CITIZEN"));
    }
}
