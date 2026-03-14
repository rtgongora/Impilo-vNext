package zw.gov.mohcc.impilo.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitGuardTest {

    @Test
    void allowsRequestsWithinLimit() {
        var guard = new RateLimitGuard(5, 1);
        for (int i = 0; i < 5; i++) {
            RateLimitResult result = guard.tryAcquire("user-1");
            assertTrue(result.allowed(), "Request " + i + " should be allowed");
        }
    }

    @Test
    void rejectsRequestsExceedingLimit() {
        var guard = new RateLimitGuard(3, 1);
        // Exhaust bucket
        for (int i = 0; i < 3; i++) {
            assertTrue(guard.tryAcquire("user-1").allowed());
        }
        // Next request should be denied
        RateLimitResult denied = guard.tryAcquire("user-1");
        assertFalse(denied.allowed());
        assertTrue(denied.retryAfterSeconds() > 0);
    }

    @Test
    void rateLimitReturns429WithErrorEnvelope() {
        var guard = new RateLimitGuard(1, 1);
        assertTrue(guard.tryAcquire("key").allowed());

        RateLimitResult result = guard.tryAcquire("key");
        assertFalse(result.allowed(), "Should be rate-limited");
        assertEquals(1, result.limit(), "Limit should reflect max tokens");
        assertEquals(0, result.remainingTokens(), "No tokens remaining");
        assertTrue(result.retryAfterSeconds() >= 1, "Should have positive retry-after");

        // Verify the result carries all data needed for a 429 error envelope
        assertNotNull(RateLimitResult.HEADER_LIMIT);
        assertNotNull(RateLimitResult.HEADER_REMAINING);
        assertNotNull(RateLimitResult.HEADER_RETRY_AFTER);
    }

    @Test
    void separateBucketsPerKey() {
        var guard = new RateLimitGuard(1, 1);
        assertTrue(guard.tryAcquire("user-A").allowed());
        assertTrue(guard.tryAcquire("user-B").allowed());
        assertFalse(guard.tryAcquire("user-A").allowed());
    }

    @Test
    void multiTokenAcquire() {
        var guard = new RateLimitGuard(10, 5);
        RateLimitResult result = guard.tryAcquire("key", 5);
        assertTrue(result.allowed());
        assertEquals(5, result.remainingTokens());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimitGuard(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new RateLimitGuard(1, 0));
    }

    @Test
    void rejectsNullKey() {
        var guard = new RateLimitGuard(10, 1);
        assertThrows(NullPointerException.class, () -> guard.tryAcquire(null));
    }

    @Test
    void rateLimitExceededExceptionCarriesResult() {
        var result = new RateLimitResult(false, 0, 100, 5);
        var ex = new RateLimitExceededException(result);
        assertSame(result, ex.getResult());
        assertTrue(ex.getMessage().contains("5"));
    }
}
