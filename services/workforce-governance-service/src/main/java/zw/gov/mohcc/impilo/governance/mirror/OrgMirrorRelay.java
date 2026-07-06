package zw.gov.mohcc.impilo.governance.mirror;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event-driven relay: consumes {@link OrgMirrorEvent}s emitted by the
 * organisation write-path and pushes each to the organization-registry mirror.
 *
 * <p>The listener fires {@code AFTER_COMMIT}, so it only ever runs once the
 * primary {@code wgv_organisation} transaction has durably committed. This is
 * the decoupling seam that guarantees the mirror HTTP call can never fail or
 * roll back the primary write. As belt-and-braces the call is additionally
 * wrapped in try/catch and the client itself swallows failures.
 *
 * <p>Gated behind {@code impilo.org-mirror.enabled} (default false): when off,
 * events are consumed and dropped without any mirror traffic.
 */
@Component
public class OrgMirrorRelay {

    private static final Logger log = LoggerFactory.getLogger(OrgMirrorRelay.class);

    private final OrgRegistryMirrorClient mirrorClient;
    private final OrgMirrorProperties properties;

    public OrgMirrorRelay(OrgRegistryMirrorClient mirrorClient, OrgMirrorProperties properties) {
        this.mirrorClient = mirrorClient;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrgMirrorEvent(OrgMirrorEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            mirrorClient.push(event.payload(), event.tenantId(), event.correlationId());
        } catch (Exception e) {
            // Defensive: client.push already swallows, but never let a mirror
            // problem escape the AFTER_COMMIT callback.
            log.warn("Org mirror relay swallowed error for organisation id={}: {}",
                    event.payload() != null ? event.payload().id() : null, e.toString());
        }
    }
}
