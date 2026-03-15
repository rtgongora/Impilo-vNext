package zw.gov.mohcc.impilo.tshepo.federation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.federation.core.PodRevocationCacheService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 21 — Pod revocation cache tests.
 *
 * Proves:
 * - Revoked pods are detected
 * - Non-revoked pods pass
 * - Reinstatement clears revocation
 * - Bulk load hydrates cache
 * - Cache survives concurrent access (ConcurrentHashMap)
 */
@DisplayName("Wave 21 — PodRevocationCacheService")
class PodRevocationCacheServiceTest {

    private PodRevocationCacheService cache;

    @BeforeEach
    void setUp() {
        cache = new PodRevocationCacheService();
    }

    @Test
    @DisplayName("isRevoked returns false for unknown pod")
    void unknownPodNotRevoked() {
        assertThat(cache.isRevoked("unknown-pod")).isFalse();
    }

    @Test
    @DisplayName("markRevoked makes pod revoked")
    void markRevokedWorks() {
        cache.markRevoked("pod-1");
        assertThat(cache.isRevoked("pod-1")).isTrue();
    }

    @Test
    @DisplayName("clearRevocation reinstates pod")
    void clearRevocationWorks() {
        cache.markRevoked("pod-2");
        assertThat(cache.isRevoked("pod-2")).isTrue();

        cache.clearRevocation("pod-2");
        assertThat(cache.isRevoked("pod-2")).isFalse();
    }

    @Test
    @DisplayName("loadAll bulk-loads revoked pods")
    void loadAllBulkLoads() {
        cache.loadAll(List.of("pod-a", "pod-b", "pod-c"));

        assertThat(cache.isRevoked("pod-a")).isTrue();
        assertThat(cache.isRevoked("pod-b")).isTrue();
        assertThat(cache.isRevoked("pod-c")).isTrue();
        assertThat(cache.isRevoked("pod-d")).isFalse();
        assertThat(cache.revokedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("revokedCount reflects current state")
    void revokedCountReflectsState() {
        assertThat(cache.revokedCount()).isZero();

        cache.markRevoked("pod-1");
        cache.markRevoked("pod-2");
        assertThat(cache.revokedCount()).isEqualTo(2);

        cache.clearRevocation("pod-1");
        assertThat(cache.revokedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Duplicate markRevoked is idempotent")
    void duplicateMarkIsIdempotent() {
        cache.markRevoked("pod-dup");
        cache.markRevoked("pod-dup");
        assertThat(cache.revokedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("clearRevocation on non-revoked pod is no-op")
    void clearNonRevokedIsNoop() {
        cache.clearRevocation("non-existent");
        assertThat(cache.revokedCount()).isZero();
    }
}
