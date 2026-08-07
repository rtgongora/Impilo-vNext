package zw.gov.mohcc.impilo.experience.trust.shadow;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends shadow observations to the PDP, and is structurally incapable of slowing a real request.
 *
 * <h2>Two pools, on purpose</h2>
 * <p>The async probe answers <em>which routes and personas would change</em> and runs at 100% of
 * preview traffic. The canary answers <em>what would this cost if it were inline</em> and runs on a
 * small fraction. They are separated because a backlog on the census would otherwise be measured as
 * the cost of the probe — the canary would report queue delay and call it latency. Both queues are
 * <b>bounded</b>: an unbounded queue turns a PDP slowdown into a BFF heap exhaustion, which is a
 * measurement taking down the thing it measures.</p>
 *
 * <h2>Overflow is counted, never silent</h2>
 * <p>A dataset that quietly omits the requests made while the estate was busiest looks complete and
 * is wrong in the direction that matters. {@link #snapshot()} reports every drop.</p>
 *
 * <h2>Its own client</h2>
 * <p>This builds a private {@link RestTemplate} rather than injecting {@code serviceRestTemplate}.
 * Two reasons, both load-bearing: the shared template's interceptor reads the <em>current</em>
 * request from {@code RequestContextHolder}, which is thread-bound and simply absent on an executor
 * thread; and the probe key must be attachable to exactly one destination. A shared client carrying
 * this header is a shared client that can leak it to ~100 domain services.</p>
 */
@Component
public class ShadowProbeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ShadowProbeDispatcher.class);

    /** The fixed contract. Declared once; never rendered into a log or an exception message. */
    public static final String PROBE_KEY_HEADER = "X-Impilo-Shadow-Probe-Key";
    public static final String SHADOW_PATH = "/v1/authorize/shadow";

    static final String MODE_ASYNC = "ASYNC";
    static final String MODE_INLINE_CANARY = "INLINE_CANARY";

    private final ShadowObservationProperties properties;
    private final String shadowUrl;
    private final RestTemplate restTemplate;

    private final ThreadPoolExecutor asyncPool;
    private final ThreadPoolExecutor canaryPool;

    private final AtomicLong eligible = new AtomicLong();
    private final AtomicLong skippedNoBearer = new AtomicLong();
    private final AtomicLong skippedNoVerdict = new AtomicLong();
    private final AtomicLong dispatched = new AtomicLong();
    private final AtomicLong droppedQueueFull = new AtomicLong();
    private final AtomicLong transportErrors = new AtomicLong();
    private final AtomicLong canaryTimeouts = new AtomicLong();
    private final AtomicLong canaryCompleted = new AtomicLong();

    /**
     * Ring buffer of canary round trips, in milliseconds. Bounded and overwriting: the percentiles
     * we want describe the estate as it is now, and an unbounded history would both leak and dilute
     * a regression with hours of healthy samples.
     */
    private final int[] canaryMillis = new int[1024];
    private final AtomicInteger canaryWriteIndex = new AtomicInteger();

    public ShadowProbeDispatcher(
            ShadowObservationProperties properties,
            @Value("${impilo.services.tshepo-authz-base-url:http://tshepo-authz-service:8081}")
            String tshepoAuthzBaseUrl) {
        this.properties = properties;
        // The PDP directly, by cluster DNS. Deliberately NOT through Envoy: the shadow probe is not
        // a user request and must never consume an ext_authz decision of its own.
        this.shadowUrl = tshepoAuthzBaseUrl + SHADOW_PATH;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()));
        this.restTemplate = new RestTemplate(factory);

        this.asyncPool = pool("impilo-shadow-async", properties.getAsyncPoolSize(),
                properties.getAsyncQueueCapacity());
        this.canaryPool = pool("impilo-shadow-canary", properties.getCanaryPoolSize(),
                properties.getCanaryQueueCapacity());
    }

    private static ThreadPoolExecutor pool(String name, int size, int queueCapacity) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                Math.max(1, size), Math.max(1, size), 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueCapacity)),
                runnable -> {
                    Thread t = new Thread(runnable, name);
                    // Daemon: a measurement must never be the reason a pod refuses to shut down.
                    t.setDaemon(true);
                    return t;
                });
        // AbortPolicy, explicitly. CallerRunsPolicy would be the natural-looking choice and is the
        // wrong one: it executes the probe ON THE REQUEST THREAD under exactly the load where the
        // request thread is scarcest, converting a shadow backlog into user-visible latency.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    /**
     * Package-private, for the test that proves the probe key reaches exactly one URL and nothing
     * else. Deliberately not public: a shared handle on this client is a handle on the header.
     */
    RestTemplate restTemplate() { return restTemplate; }

    /** The single destination this dispatcher may ever call. */
    String shadowUrl() { return shadowUrl; }

    // Coverage counters live here rather than on the filter because the filter is deliberately not
    // a bean (see SecurityConfig): a filter published as a bean is also auto-registered outside the
    // security chain. A reporter reading counters off a second, unused instance would print zeroes
    // and read as "nothing to observe" — the exact failure mode this measurement cannot afford.
    void markEligible() { eligible.incrementAndGet(); }
    void markSkippedNoBearer() { skippedNoBearer.incrementAndGet(); }
    void markSkippedNoVerdict() { skippedNoVerdict.incrementAndGet(); }

    /** Fire and forget. Returns immediately; a full queue is a counted drop, not a wait. */
    void dispatchAsync(ShadowRequestEnvelope envelope) {
        try {
            asyncPool.execute(() -> send(envelope, MODE_ASYNC, queueDelayMillis(envelope)));
        } catch (RejectedExecutionException e) {
            droppedQueueFull.incrementAndGet();
        }
    }

    /**
     * Measure the probe as an inline cost would be measured — serialized, on the request thread —
     * but with a hard ceiling, so a hung PDP cannot pin request threads for its read timeout.
     *
     * <p>Called after the response has been written, so the wall clock recorded here is the cost an
     * inline probe <em>would</em> add without actually adding it to what the browser waits for.
     * That distinction is stated because the number is the whole point of the canary: it is a
     * faithful measure of the serialized round trip, taken at a moment where paying for it costs
     * the user nothing.</p>
     */
    void runInlineCanary(ShadowRequestEnvelope envelope) {
        Future<?> future;
        try {
            future = canaryPool.submit(() -> send(envelope, MODE_INLINE_CANARY, 0));
        } catch (RejectedExecutionException e) {
            droppedQueueFull.incrementAndGet();
            return;
        }
        long started = System.nanoTime();
        try {
            future.get(properties.getCanaryTimeoutMillis(), TimeUnit.MILLISECONDS);
            recordCanary((int) ((System.nanoTime() - started) / 1_000_000L));
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            canaryTimeouts.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            transportErrors.incrementAndGet();
        }
    }

    /**
     * Milliseconds between capture on the request thread and the probe actually starting. For the
     * async path this is queue delay, and it is the number that says whether the census is keeping
     * up. It is sent as {@code totalAddedMillis} because a call cannot report its own round trip in
     * its own body — the round trip is measured client-side, in {@link #snapshot()}.
     */
    private static int queueDelayMillis(ShadowRequestEnvelope envelope) {
        return (int) ((System.nanoTime() - envelope.capturedAtNanos()) / 1_000_000L);
    }

    private void send(ShadowRequestEnvelope envelope, String mode, int addedMillis) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(PROBE_KEY_HEADER, properties.getProbeKey());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("method", envelope.method());
            body.put("path", envelope.path());
            body.put("prodVerdict", envelope.prodVerdict());
            body.put("prodActorType", envelope.prodActorType());
            body.put("trustContext", envelope.trustContext());
            body.put("bearer", envelope.bearer());
            body.put("probeMode", mode);
            body.put("totalAddedMillis", addedMillis);

            restTemplate.postForEntity(shadowUrl, new HttpEntity<>(body, headers), Void.class);
            dispatched.incrementAndGet();
            if (MODE_INLINE_CANARY.equals(mode)) {
                canaryCompleted.incrementAndGet();
            }
        } catch (Exception e) {
            // Never log the exception object or the envelope. A RestTemplate exception renders the
            // request it failed on, and the body of this one is a bearer token. Class name only.
            transportErrors.incrementAndGet();
            if (transportErrors.get() % 100 == 1) {
                log.warn("shadow probe transport failure ({} so far): {} — measurement only, "
                        + "no request was affected", transportErrors.get(), e.getClass().getSimpleName());
            }
        }
    }

    private void recordCanary(int millis) {
        int slot = Math.floorMod(canaryWriteIndex.getAndIncrement(), canaryMillis.length);
        canaryMillis[slot] = millis;
    }

    /** Health and latency, for the evidence report. Reports what was lost, not only what was kept. */
    public Snapshot snapshot() {
        int written = canaryWriteIndex.get();
        int n = Math.min(written, canaryMillis.length);
        int[] sample = Arrays.copyOf(canaryMillis, n);
        Arrays.sort(sample);
        return new Snapshot(
                eligible.get(), skippedNoBearer.get(), skippedNoVerdict.get(),
                dispatched.get(), droppedQueueFull.get(), transportErrors.get(),
                canaryTimeouts.get(), canaryCompleted.get(),
                asyncPool.getQueue().size(), canaryPool.getQueue().size(),
                n, percentile(sample, 50), percentile(sample, 95), percentile(sample, 99));
    }

    /** Nearest-rank. No interpolation: with a bounded sample, an interpolated p99 invents a value. */
    static Integer percentile(int[] sorted, int p) {
        if (sorted.length == 0) return null;
        int rank = (int) Math.ceil(p / 100.0 * sorted.length);
        return sorted[Math.min(sorted.length, Math.max(1, rank)) - 1];
    }

    public record Snapshot(long eligible, long skippedNoBearer, long skippedNoVerdict,
                           long dispatched, long droppedQueueFull, long transportErrors,
                           long canaryTimeouts, long canaryCompleted,
                           int asyncQueueDepth, int canaryQueueDepth,
                           int canarySamples, Integer canaryP50, Integer canaryP95, Integer canaryP99) {}

    @PreDestroy
    void shutdown() {
        asyncPool.shutdownNow();
        canaryPool.shutdownNow();
    }
}
