package zw.gov.mohcc.impilo.sharedkernel.security;

/**
 * Persists the record of an emergency action allowed without a validated grant.
 *
 * <h2>Implementation contract — the mirror image of the client's</h2>
 *
 * <p>{@link BreakGlassGrantClient} must never throw, so that an outage cannot be mistaken for a
 * refusal. This interface is the opposite: <b>an implementation MUST throw if it cannot record.</b>
 * Swallowing the failure would produce an allow with no trace, which is the ungoverned override
 * this whole mechanism exists to make visible — occurring silently, inside the code written to
 * expose it.</p>
 *
 * <p>The guard calls this <em>before</em> it returns its allow, and does not catch what comes back.
 * So a recorder that cannot write turns the emergency action into a refusal. That sounds like it
 * contradicts the PO ruling, and it does not: the ruling protects against <em>tshepo-authz</em>
 * being unavailable, which is a remote dependency the clinical action does not otherwise need. A
 * recorder failure means the service's own database is gone — and the clinical write the guard is
 * protecting would fail a moment later regardless. There is no case where refusing here costs care
 * that would otherwise have been delivered.</p>
 *
 * <p>Implementations should therefore write somewhere local and durable — the service's own
 * transactional outbox — rather than making a second synchronous call to another service. The
 * moment this runs is precisely the moment the network is known to be unreliable.</p>
 */
@FunctionalInterface
public interface UngovernedOverrideRecorder {

    /**
     * Durably record an ungoverned override.
     *
     * @throws RuntimeException if the record cannot be persisted. Do not catch this inside an
     *                          implementation and return normally — an allow without a record is
     *                          strictly worse than a refusal.
     */
    void record(UngovernedOverride override);
}
