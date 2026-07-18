package zw.gov.mohcc.impilo.sharedkernel.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fans a single {@code vito.merge.executed} event out to every {@link IdentityRepointHook} a service
 * exposes, so all of that service's trauma tables repoint {@code oldSubjectCpid → newSubjectCpid}
 * in one delivery. Each service wires this with its Spring {@code List<IdentityRepointHook>} — which
 * auto-collects both the trauma lane's own hooks and peers' hooks registered in the same context
 * (e.g. inpatient collects the resuscitation hook AND theatre's procedure-episode hook).
 *
 * <p>Framework-agnostic: the service's Kafka listener (prod) or no-Kafka merge endpoint/drainer (the
 * runtime-proof rig) both call {@link #dispatch}. Repointing is naturally idempotent — a redelivered
 * merge finds no rows still on the tombstoned id and repoints zero — so no processed-merge ledger is
 * required for correctness.</p>
 */
public final class VitoMergeRepointDispatcher {

    private static final Logger log = LoggerFactory.getLogger(VitoMergeRepointDispatcher.class);

    private final List<IdentityRepointHook> hooks;

    public VitoMergeRepointDispatcher(List<IdentityRepointHook> hooks) {
        this.hooks = hooks != null ? hooks : List.of();
    }

    /** Parse a raw {@code vito.merge.executed} payload and dispatch to every hook. */
    public Map<String, Integer> dispatch(String eventPayloadJson) {
        return dispatch(IdentityRepointHandler.parse(eventPayloadJson));
    }

    /** Dispatch an already-parsed merge command to every hook; returns per-participant row counts. */
    public Map<String, Integer> dispatch(IdentityRepointCommand command) {
        Map<String, Integer> repointed = new LinkedHashMap<>();
        for (IdentityRepointHook hook : hooks) {
            try {
                int rows = hook.repoint(command);
                repointed.put(hook.participant(), rows);
                log.info("vito.merge.executed → {} repointed {} row(s) ({}→{})",
                        hook.participant(), rows, command.oldSubjectCpid(), command.newSubjectCpid());
            } catch (RuntimeException e) {
                log.error("identity repoint hook {} failed for merge {}→{}: {}",
                        hook.participant(), command.oldSubjectCpid(), command.newSubjectCpid(), e.getMessage(), e);
                repointed.put(hook.participant(), -1);
            }
        }
        return repointed;
    }

    public int hookCount() {
        return hooks.size();
    }
}
