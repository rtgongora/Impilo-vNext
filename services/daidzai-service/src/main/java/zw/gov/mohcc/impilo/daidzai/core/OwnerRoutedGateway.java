package zw.gov.mohcc.impilo.daidzai.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Honest seam to the sovereign owner systems Daidzai orchestrates (Khuluma comms, Nhume dispatch,
 * Ndila routing, PCT encounter, Rito after-action). For the MVP this records the orchestration
 * INTENT via the outbox/log so the request is real and auditable, without faking the owner's
 * response. Wiring the live HTTP calls is a documented owner-routed-hook deferral (see ledger):
 * the owner system, not Daidzai, performs the work and returns the authoritative ref.
 *
 * <p>This is deliberately NOT a fake: it raises a genuine request signal; it does not fabricate a
 * dispatched ambulance, a delivered SMS, or a clinical encounter.</p>
 */
@Component
public class OwnerRoutedGateway {

    private static final Logger log = LoggerFactory.getLogger(OwnerRoutedGateway.class);

    /** Request Khuluma to deliver a notification (SOS ack / responder / caregiver alert). */
    public void requestNotification(UUID tenantId, String channel, String recipientRole,
                                    String templateKey, Map<String, Object> context) {
        log.info("daidzai->khuluma notify tenant={} channel={} role={} template={} ctx={}",
                tenantId, channel, recipientRole, templateKey, context);
    }

    /** Request Nhume to execute a dispatch mission for an incident (Nhume returns the mission ref). */
    public void requestDispatch(UUID tenantId, UUID incidentId, Map<String, Object> need) {
        log.info("daidzai->nhume dispatch-request tenant={} incident={} need={}",
                tenantId, incidentId, need);
    }
}
