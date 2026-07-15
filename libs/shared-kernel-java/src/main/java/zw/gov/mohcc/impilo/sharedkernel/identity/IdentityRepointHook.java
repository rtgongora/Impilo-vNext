package zw.gov.mohcc.impilo.sharedkernel.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-service hook that repoints a participant's own rows when a VITO merge collapses two
 * identities. Each service that stamps {@code patient_cpid} / a Health ID onto trauma rows
 * provides one implementation that, inside its own transaction and idempotently, rewrites
 * {@code mergedHealthId → survivorHealthId} across its tables and writes an audit row.
 *
 * <p>The {@link #noop(String)} implementation is the W0 skeleton wiring: it records that the
 * merge reached the participant without changing any rows, so services can subscribe to
 * {@code vito.merge.executed} before their real repoint logic lands (W6). Repointing zero rows
 * is a legitimate outcome (the participant simply held no rows for that patient).</p>
 */
public interface IdentityRepointHook {

    /**
     * Repoint all rows this participant owns from {@code command.mergedHealthId()} to
     * {@code command.survivorHealthId()}. Implementations MUST be idempotent (re-applying the
     * same command changes nothing) and MUST audit the operation.
     *
     * @return the number of rows repointed (0 is valid — the participant held none).
     */
    int repoint(IdentityRepointCommand command);

    /** Registered participant name (e.g. {@code "pct-service"}) — for dispatch logging and audit. */
    String participant();

    /**
     * Skeleton no-op hook: proves the propagation channel reaches a participant without mutating
     * data. Replace per service with a real repoint in W6.
     */
    static IdentityRepointHook noop(String participant) {
        return new IdentityRepointHook() {
            private final Logger log = LoggerFactory.getLogger(
                    "IdentityRepointHook." + participant);

            @Override
            public int repoint(IdentityRepointCommand command) {
                log.info("no-op identity repoint for {} — merge {} ({}→{}); no real repoint wired yet",
                        participant, command.mergeId(), command.mergedHealthId(),
                        command.survivorHealthId());
                return 0;
            }

            @Override
            public String participant() {
                return participant;
            }
        };
    }
}
