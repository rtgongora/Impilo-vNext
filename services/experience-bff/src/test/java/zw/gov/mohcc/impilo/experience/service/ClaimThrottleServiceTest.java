package zw.gov.mohcc.impilo.experience.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("ClaimThrottleService")
class ClaimThrottleServiceTest {

    private StringRedisTemplate redis;
    private ClaimThrottleService service;
    private Map<String, Long> counters;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        counters = new HashMap<>();
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(i -> {
            String key = i.getArgument(0);
            return counters.merge(key, 1L, Long::sum);
        });
        when(redis.expire(anyString(), any())).thenReturn(true);
        service = new ClaimThrottleService(redis);
    }

    @Test
    @DisplayName("allows up to the per-account cap, then throttles")
    void throttlesPerAccount() {
        // 5 starts allowed, the 6th is throttled.
        for (int i = 0; i < 5; i++) {
            assertThat(service.allowStart("t1", "acct-1", "10.0.0.1")).isTrue();
        }
        assertThat(service.allowStart("t1", "acct-1", "10.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("a different account is not affected by another account's throttle")
    void perAccountIsolation() {
        for (int i = 0; i < 6; i++) {
            service.allowStart("t1", "acct-1", "10.0.0.1");
        }
        assertThat(service.allowStart("t1", "acct-2", "10.0.0.2")).isTrue();
    }

    @Test
    @DisplayName("fails open when Redis is unavailable (OTP-to-contact stays the hard gate)")
    void failsOpenOnRedisError() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));
        assertThat(service.allowStart("t1", "acct-1", "10.0.0.1")).isTrue();
    }
}
