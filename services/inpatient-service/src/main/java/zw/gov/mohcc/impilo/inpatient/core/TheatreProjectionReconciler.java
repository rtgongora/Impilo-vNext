package zw.gov.mohcc.impilo.inpatient.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureBloodLinkEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureSpecimenEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureTransportEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureBloodLinkRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureSpecimenRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureTransportRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Polls the open theatre LINK/PROJECTION rows and re-syncs each from its peer owner (MADI blood order,
 * NHUME delivery, OROS specimen/result), firing overdue/critical SAFETY outbox events. Because it runs
 * off the request thread it establishes a per-row system TrustContext (using the row's tenant) so peer
 * read-throughs carry the tenant header. Idempotent by construction — the reconcile* methods only move
 * a projection forward and each SAFETY event is emitted once (guarded by an overdue/critical flag).
 * Disabled under the "test" profile so unit/integration tests are deterministic.
 */
@Component
@Profile("!test")
public class TheatreProjectionReconciler {

    private static final Logger log = LoggerFactory.getLogger(TheatreProjectionReconciler.class);

    private static final List<String> BLOOD_TERMINAL = List.of("TRANSFUSED", "RECONCILED", "RETURNED", "NOT_REQUIRED");
    private static final List<String> TRANSPORT_TERMINAL = List.of("DELIVERED", "FAILED", "CANCELLED");
    private static final List<String> SPECIMEN_TERMINAL = List.of("RESULTED", "ACKNOWLEDGED", "REJECTED");

    private final TheatreService theatreService;
    private final ProcedureBloodLinkRepository bloodLinkRepository;
    private final ProcedureTransportRepository transportRepository;
    private final ProcedureSpecimenRepository specimenRepository;

    public TheatreProjectionReconciler(TheatreService theatreService,
                                       ProcedureBloodLinkRepository bloodLinkRepository,
                                       ProcedureTransportRepository transportRepository,
                                       ProcedureSpecimenRepository specimenRepository) {
        this.theatreService = theatreService;
        this.bloodLinkRepository = bloodLinkRepository;
        this.transportRepository = transportRepository;
        this.specimenRepository = specimenRepository;
    }

    @Scheduled(fixedDelayString = "${inpatient.theatre.reconciler.fixed-delay:5000}",
            initialDelayString = "${inpatient.theatre.reconciler.initial-delay:15000}")
    public void reconcile() {
        try {
            for (ProcedureBloodLinkEntity link : bloodLinkRepository.findByStatusNotIn(BLOOD_TERMINAL)) {
                runAsTenant(link.getTenantId(), () -> theatreService.reconcileBloodLink(link));
            }
            for (ProcedureTransportEntity t : transportRepository.findByStatusNotIn(TRANSPORT_TERMINAL)) {
                runAsTenant(t.getTenantId(), () -> theatreService.reconcileTransport(t));
            }
            for (ProcedureSpecimenEntity s : specimenRepository.findByStatusNotIn(SPECIMEN_TERMINAL)) {
                runAsTenant(s.getTenantId(), () -> theatreService.reconcileSpecimen(s));
            }
        } catch (RuntimeException e) {
            // Never let the scheduler die — log and retry on the next tick.
            log.warn("INPATIENT-THEATRE: reconciler pass failed (will retry): {}", e.getMessage());
        }
    }

    private void runAsTenant(UUID tenantId, Runnable work) {
        if (tenantId == null) return;
        TrustContextHolder.set(new TrustContext(tenantId, "system-theatre-reconciler", "SYSTEM",
                "TREATMENT", null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        try {
            work.run();
        } catch (RuntimeException e) {
            log.warn("INPATIENT-THEATRE: reconcile row failed for tenant {} (best-effort): {}", tenantId, e.getMessage());
        } finally {
            TrustContextHolder.clear();
        }
    }
}
