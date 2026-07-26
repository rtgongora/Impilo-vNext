package zw.gov.mohcc.impilo.orgregistry.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Drives {@link RegulatoryAppointmentService#expireLapsed(LocalDate)} on a schedule (RB-1, V008).
 *
 * <p>Thin by design: the transition and its event live in the service, beside the other lifecycle
 * transitions and the single outbox writer, so there is one place that decides what an
 * {@code ended} appointment event looks like. This class only decides <em>when</em>.</p>
 *
 * <p>Mirrors vashandi's {@code AssignmentExpirySweep} and varapi's {@code LicenceRenewalSweep} —
 * the same idea applied to the third thing in the estate that carries an expiry date, and the only
 * one that had nobody reading it.</p>
 */
@Component
public class RegulatoryAppointmentExpirySweep {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryAppointmentExpirySweep.class);

    private final RegulatoryAppointmentService appointmentService;

    public RegulatoryAppointmentExpirySweep(RegulatoryAppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @Scheduled(fixedDelayString = "${impilo.orgregistry.appointment-sweep.interval-ms:3600000}")
    public void sweep() {
        try {
            int ended = appointmentService.expireLapsed(LocalDate.now());
            if (ended > 0) {
                log.info("Regulatory appointment expiry sweep: {} ended", ended);
            }
        } catch (Exception e) {
            // A sweep failure must be loud. Silence here reads exactly like "nothing had expired",
            // which is the failure mode that let valid_to go unread for as long as it did.
            log.error("Regulatory appointment expiry sweep failed: {}", e.getMessage(), e);
        }
    }
}
