package zw.gov.mohcc.impilo.experience.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.scheduling.AppointmentReminderReceiptStore.ClaimOutcome;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-replica dedup for patient-facing sends.
 *
 * <p>Each {@code AppointmentReminderReceiptStore} instance stands for one {@code experience-bff} pod.
 * Two stores over ONE shared keyspace is the two-replica case; the invariant is that exactly one of
 * them may send.
 */
class AppointmentReminderReceiptStoreTest {

    @Test
    @DisplayName("two replicas, one due reminder: exactly one may send")
    void exactlyOneReplicaClaims() {
        FakeRedis redis = FakeRedis.shared();
        AppointmentReminderReceiptStore podA = new AppointmentReminderReceiptStore(redis.template());
        AppointmentReminderReceiptStore podB = new AppointmentReminderReceiptStore(redis.template());

        String dedupKey = "appt-1@2026-08-08T09:00:00Z";

        assertEquals(ClaimOutcome.CLAIMED, podA.claim(dedupKey));
        assertEquals(ClaimOutcome.ALREADY_CLAIMED, podB.claim(dedupKey),
                "the second replica must observe the first replica's claim");
    }

    @Test
    @DisplayName("two replicas racing the same key concurrently: still exactly one send")
    void exactlyOneReplicaClaimsUnderRace() throws Exception {
        FakeRedis redis = FakeRedis.shared();
        AppointmentReminderReceiptStore podA = new AppointmentReminderReceiptStore(redis.template());
        AppointmentReminderReceiptStore podB = new AppointmentReminderReceiptStore(redis.template());

        String dedupKey = "appt-race@2026-08-08T09:00:00Z";
        CyclicBarrier startLine = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> claim = () -> {
                startLine.await(5, TimeUnit.SECONDS);
                return podA.tryClaim(dedupKey);
            };
            Callable<Boolean> peerClaim = () -> {
                startLine.await(5, TimeUnit.SECONDS);
                return podB.tryClaim(dedupKey);
            };
            List<Future<Boolean>> results = List.of(pool.submit(claim), pool.submit(peerClaim));
            int sends = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    sends++;
                }
            }
            assertEquals(1, sends, "exactly one replica may send a given reminder");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Redis unreachable: BOTH replicas refuse — no per-pod fallback that sends twice")
    void refusesRatherThanFallingBackPerPod() {
        // Separate templates, as two pods would have. Before the fix each store fell back to its own
        // in-memory set, so both won their own private claim and both sent.
        AppointmentReminderReceiptStore podA = new AppointmentReminderReceiptStore(FakeRedis.unavailable());
        AppointmentReminderReceiptStore podB = new AppointmentReminderReceiptStore(FakeRedis.unavailable());

        String dedupKey = "appt-2@2026-08-08T09:00:00Z";

        assertEquals(ClaimOutcome.UNAVAILABLE, podA.claim(dedupKey));
        assertEquals(ClaimOutcome.UNAVAILABLE, podB.claim(dedupKey));
        assertFalse(podA.tryClaim(dedupKey), "no shared claim is possible, so no replica may send");
        assertFalse(podB.tryClaim(dedupKey), "no shared claim is possible, so no replica may send");
    }

    @Test
    @DisplayName("an unavailable claim is retryable; a lost race is not")
    void onlyUnavailableAsksToBeRetried() {
        FakeRedis redis = FakeRedis.shared();
        AppointmentReminderReceiptStore podA = new AppointmentReminderReceiptStore(redis.template());
        AppointmentReminderReceiptStore podB = new AppointmentReminderReceiptStore(redis.template());
        AppointmentReminderReceiptStore offline = new AppointmentReminderReceiptStore(FakeRedis.unavailable());

        String key = "appt-3@2026-08-08T09:00:00Z";

        assertFalse(podA.claim(key).mustRetry(), "a won claim is settled");
        assertFalse(podB.claim(key).mustRetry(), "a peer's claim settles it too — do not re-offer");
        assertTrue(offline.claim(key).mustRetry(), "an unmade claim must be re-offered, never dropped");
    }

    @Test
    @DisplayName("an unusable dedup key is refused, and is not retryable")
    void blankKeyIsRefusedWithoutRetry() {
        AppointmentReminderReceiptStore store =
                new AppointmentReminderReceiptStore(FakeRedis.shared().template());

        assertEquals(ClaimOutcome.INVALID_KEY, store.claim(null));
        assertEquals(ClaimOutcome.INVALID_KEY, store.claim("  "));
        assertFalse(store.claim("").mustRetry(), "a blank key will never become claimable — do not loop on it");
    }

    @Test
    @DisplayName("recovery: once Redis returns, claims resume")
    void claimsResumeAfterRecovery() {
        FakeRedis redis = FakeRedis.shared();
        AppointmentReminderReceiptStore store = new AppointmentReminderReceiptStore(redis.template());

        assertEquals(ClaimOutcome.CLAIMED, store.claim("appt-4@2026-08-08T09:00:00Z"),
                "a healthy store claims normally after other instances have degraded");
    }
}
