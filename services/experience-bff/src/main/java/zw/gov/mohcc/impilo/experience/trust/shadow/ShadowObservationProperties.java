package zw.gov.mohcc.impilo.experience.trust.shadow;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the P1 shadow observer.
 *
 * <p><b>TEMPORARY BY DESIGN.</b> This is the BFF half of a measurement that exists because the
 * browser holds an opaque session cookie, sends no {@code Authorization} header, and therefore
 * resolves no roles at the PDP — while 489 of 525 active ALLOW rules require one. It observes and
 * reports; it changes no request, no response and no verdict. When the measurement is read and
 * acted on, delete it.</p>
 *
 * <p>{@link #probeKey} answers <em>may this workload call the temporary shadow API</em> and nothing
 * about any human. It is supplied by deployment configuration only, never committed, and a blank
 * value disables dispatch entirely rather than calling an endpoint that would refuse it.</p>
 */
@Component
@ConfigurationProperties(prefix = "impilo.trust.shadow-bearer")
public class ShadowObservationProperties {

    /** Master gate. While false nothing is captured, no bearer is read, and no probe is sent. */
    private boolean enabled = false;

    /** Shared secret for {@code X-Impilo-Shadow-Probe-Key}. Blank means "do not dispatch". */
    private String probeKey = "";

    /**
     * Fraction of eligible requests observed asynchronously. 1.0 in preview: the deliverable is a
     * census of which routes and personas would change, and a sample cannot say that a rule never
     * fires.
     */
    private double asyncSampleRate = 1.0;

    /**
     * Fraction of eligible requests measured on the request thread. Deliberately small: this one
     * answers "what would an inline probe cost?", which needs a modest number of clean samples, not
     * a census.
     */
    private double canarySampleRate = 0.02;

    /** Threads dedicated to async probes. Shadow must never contend with request handling. */
    private int asyncPoolSize = 2;

    /**
     * Bounded queue. A queue that grows is a heap leak that presents as a memory alarm hours after
     * the cause; overflow is DROPPED and counted instead.
     */
    private int asyncQueueCapacity = 500;

    /** Canary threads. Its own pool so a saturated async backlog cannot delay a request thread. */
    private int canaryPoolSize = 1;

    /** Canary queue. Tiny on purpose: a queued canary measures queue delay, not probe cost. */
    private int canaryQueueCapacity = 8;

    /** Hard ceiling on how long a canary may hold the request thread. */
    private int canaryTimeoutMillis = 750;

    private int connectTimeoutMillis = 1000;
    private int readTimeoutMillis = 2000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProbeKey() { return probeKey; }
    public void setProbeKey(String probeKey) { this.probeKey = probeKey; }
    public double getAsyncSampleRate() { return asyncSampleRate; }
    public void setAsyncSampleRate(double v) { this.asyncSampleRate = v; }
    public double getCanarySampleRate() { return canarySampleRate; }
    public void setCanarySampleRate(double v) { this.canarySampleRate = v; }
    public int getAsyncPoolSize() { return asyncPoolSize; }
    public void setAsyncPoolSize(int v) { this.asyncPoolSize = v; }
    public int getAsyncQueueCapacity() { return asyncQueueCapacity; }
    public void setAsyncQueueCapacity(int v) { this.asyncQueueCapacity = v; }
    public int getCanaryPoolSize() { return canaryPoolSize; }
    public void setCanaryPoolSize(int v) { this.canaryPoolSize = v; }
    public int getCanaryQueueCapacity() { return canaryQueueCapacity; }
    public void setCanaryQueueCapacity(int v) { this.canaryQueueCapacity = v; }
    public int getCanaryTimeoutMillis() { return canaryTimeoutMillis; }
    public void setCanaryTimeoutMillis(int v) { this.canaryTimeoutMillis = v; }
    public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
    public void setConnectTimeoutMillis(int v) { this.connectTimeoutMillis = v; }
    public int getReadTimeoutMillis() { return readTimeoutMillis; }
    public void setReadTimeoutMillis(int v) { this.readTimeoutMillis = v; }

    /** Configured, non-blank, and switched on. Anything less means "do not dispatch". */
    public boolean dispatchable() {
        return enabled && probeKey != null && !probeKey.isBlank();
    }
}
