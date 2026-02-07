package zw.gov.mohcc.impilo.tshepo.sdk;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.AccessMode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrustContextTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();

    @Test
    void hasFacilityScope_withFacilityId_returnsTrue() {
        UUID facilityId = UUID.randomUUID();

        TrustContext ctx = new TrustContext(
                TENANT_ID, "actor-1", "PROVIDER", "TREATMENT",
                "fp-abc123", CORRELATION_ID,
                facilityId, null, null, AccessMode.EXTERNAL
        );

        assertThat(ctx.hasFacilityScope()).isTrue();
    }

    @Test
    void hasFacilityScope_withoutFacilityId_returnsFalse() {
        TrustContext ctx = new TrustContext(
                TENANT_ID, "actor-1", "PROVIDER", "TREATMENT",
                "fp-abc123", CORRELATION_ID,
                null, null, null, AccessMode.EXTERNAL
        );

        assertThat(ctx.hasFacilityScope()).isFalse();
    }

    @Test
    void hasWorkspaceScope_withWorkspaceId_returnsTrue() {
        UUID workspaceId = UUID.randomUUID();

        TrustContext ctx = new TrustContext(
                TENANT_ID, "actor-1", "ADMIN", "OPERATIONS",
                "fp-abc123", CORRELATION_ID,
                null, workspaceId, null, AccessMode.EXTERNAL
        );

        assertThat(ctx.hasWorkspaceScope()).isTrue();
    }

    @Test
    void isInternal_withInternalMode_returnsTrue() {
        TrustContext ctx = new TrustContext(
                TENANT_ID, "svc-scheduler", "SERVICE", "SYSTEM",
                "fp-svc", CORRELATION_ID,
                null, null, null, AccessMode.INTERNAL
        );

        assertThat(ctx.isInternal()).isTrue();
    }
}
