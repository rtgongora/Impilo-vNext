package zw.gov.mohcc.impilo.experience.scheduling;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An in-memory stand-in for the Redis that both {@code experience-bff} replicas share.
 *
 * <p>Deliberately <b>not</b> a bare {@code mock(StringRedisTemplate.class)}: a bare mock returns
 * {@code null} from {@code opsForValue()}, so every call NPEs into the store's catch block and the
 * test silently exercises the unavailable path while appearing to exercise the happy one. Tests that
 * want the unavailable path must ask for it explicitly via {@link #unavailable()}.
 *
 * <p>One instance == one Redis. Hand the same instance to two stores to model two replicas; hand
 * them different instances to model two replicas that cannot see each other.
 */
final class FakeRedis {

    private final Map<String, String> data = new ConcurrentHashMap<>();

    private FakeRedis() {
    }

    /** A healthy shared Redis. */
    static FakeRedis shared() {
        return new FakeRedis();
    }

    /** A template whose every operation fails, as when Redis is unreachable. */
    static StringRedisTemplate unavailable() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.opsForValue()).thenThrow(new IllegalStateException("redis unreachable (test)"));
        return template;
    }

    /** A template backed by this shared keyspace. */
    StringRedisTemplate template() {
        return template(true);
    }

    /**
     * A template backed by this shared keyspace whose {@code setIfAbsent} may be forced to fail while
     * plain get/set keep working.
     *
     * <p>That split is artificial for a whole-Redis outage — but it is the only way to isolate the
     * invariant under test in {@link BookingOutboxCommsPollerTest}: that an event which could not be
     * claimed does not move the cursor. With both halves failing, the cursor cannot move anyway and
     * the test would pass without the guard it is meant to prove.
     */
    StringRedisTemplate template(boolean claimsWork) {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);

        when(template.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(inv -> data.get(inv.<String>getArgument(0)));
        org.mockito.Mockito.doAnswer(inv -> {
            data.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString());

        if (claimsWork) {
            when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenAnswer(inv -> data.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
        } else {
            when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenThrow(new IllegalStateException("redis unreachable (test)"));
        }
        return template;
    }
}
