package zw.gov.mohcc.impilo.ndila.core.policy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NdilaTshepoGuardTest {

    private final NdilaTshepoGuard guard = new NdilaTshepoGuard();

    private NdilaOperationContext ctx(NdilaSensitivity sens, String purpose, String actorId, boolean emergency) {
        return new NdilaOperationContext(
                UUID.randomUUID(), actorId, "PROVIDER", purpose, "TEST",
                "ndila-test", sens, false, emergency,
                UUID.randomUUID(), "development", "ZW");
    }

    @Test
    void allowsPublicWithoutActor() {
        assertThat(guard.authorize(ctx(NdilaSensitivity.PUBLIC, "OPS", null, false), "op").allowed())
                .isTrue();
    }

    @Test
    void deniesInternalWithoutActor() {
        var d = guard.authorize(ctx(NdilaSensitivity.INTERNAL, "OPS", null, false), "op");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("UNAUTHENTICATED_ACTOR");
    }

    @Test
    void allowsRestrictedForOperationsPurpose() {
        var d = guard.authorize(ctx(NdilaSensitivity.RESTRICTED, "DISPATCH", "actor-1", false), "op");
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void deniesRestrictedWithoutOperationalPurpose() {
        var d = guard.authorize(ctx(NdilaSensitivity.RESTRICTED, "MARKETING", "actor-1", false), "op");
        assertThat(d.allowed()).isFalse();
    }

    @Test
    void breakGlassAllowsHighlyRestrictedEmergencyAccess() {
        var d = guard.authorize(ctx(NdilaSensitivity.HIGHLY_RESTRICTED, "EMERGENCY_RESPONSE", "actor-1", true), "op");
        assertThat(d.allowed()).isTrue();
        assertThat(d.breakGlass()).isTrue();
    }
}
