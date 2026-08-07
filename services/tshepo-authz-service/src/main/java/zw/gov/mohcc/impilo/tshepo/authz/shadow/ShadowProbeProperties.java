package zw.gov.mohcc.impilo.tshepo.authz.shadow;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the P1 shadow probe.
 *
 * <p><b>TEMPORARY BY DESIGN — this is P1 probe authentication, not vNext trust architecture.</b>
 * {@link #probeKey} is a shared secret that answers one question: <em>is this an authorised
 * workload allowed to invoke the temporary shadow API?</em> It says nothing about any human. It
 * exists because the estate has no workload identity yet, and P3 is deliberately later and must
 * not block this measurement.</p>
 *
 * <p><b>When P3 lands, delete this.</b> The probe-key path is replaced by authenticated BFF
 * workload identity with an audience-bound credential — not kept as a fallback, which is how
 * temporary authentication mechanisms become permanent ones.</p>
 *
 * <p>The secret is supplied by deployment configuration only. It is never committed, and an empty
 * value fails closed: with no key configured the endpoint refuses every caller rather than
 * accepting any.</p>
 */
@Component
@ConfigurationProperties(prefix = "impilo.trust.shadow-bearer")
public class ShadowProbeProperties {

    /**
     * Master gate. While false the endpoint performs no bearer processing and no policy
     * evaluation, and the estate behaves exactly as it does today.
     */
    private boolean enabled = false;

    /** High-entropy shared secret, from a Kubernetes Secret. Blank means refuse everyone. */
    private String probeKey = "";

    /**
     * Shadow evaluations allowed to run at once, per PDP instance — an UPPER BOUND, lowered
     * further by {@link ShadowCapacityPolicy} against the actual datasource pool.
     *
     * <p>Was 4, which measured as a defect: the estate pins the Hikari pool to 3, so four
     * concurrent evaluations could exhaust it before production authorization asked for a
     * connection. Shadow must always lose resource contention to production authorization, and a
     * ceiling chosen without reference to the contended resource does not deliver that.</p>
     */
    private int maxConcurrent = 1;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProbeKey() { return probeKey; }
    public void setProbeKey(String probeKey) { this.probeKey = probeKey; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
}
