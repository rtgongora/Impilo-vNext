package zw.gov.mohcc.impilo.tshepo.identity.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.ProvisionalCpidResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.ReconcileRequest;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.ReconcileResponse;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.ProvisionalCpidEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.ProvisionalCpidRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reconciles offline-issued provisional CPIDs (O-CPIDs) to canonical CPIDs.
 *
 * <h3>Reconciliation workflow:</h3>
 * <ol>
 *   <li>Facility comes back online and submits (tenantId, oCpid, healthId)</li>
 *   <li>Service finds-or-creates the id_mapping entry (independent random CPID)</li>
 *   <li>Updates the provisional_cpid record: status=RECONCILED, canonical_cpid set</li>
 *   <li>Downstream systems can then merge any data tagged with the O-CPID</li>
 * </ol>
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ProvisionalCpidRepository provisionalRepo;
    private final EventOutboxRepository outboxRepo;
    private final CpidGenerator cpidGenerator;
    private final IdResolutionService idResolutionService;
    private final zw.gov.mohcc.impilo.tshepo.identity.events.SubjectEventPublisher subjectEvents;
    private final ObjectMapper objectMapper;

    public ReconciliationService(ProvisionalCpidRepository provisionalRepo,
                                  EventOutboxRepository outboxRepo,
                                  CpidGenerator cpidGenerator,
                                  IdResolutionService idResolutionService,
                                  zw.gov.mohcc.impilo.tshepo.identity.events.SubjectEventPublisher subjectEvents,
                                  ObjectMapper objectMapper) {
        this.provisionalRepo = provisionalRepo;
        this.outboxRepo = outboxRepo;
        this.cpidGenerator = cpidGenerator;
        this.idResolutionService = idResolutionService;
        this.subjectEvents = subjectEvents;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate and persist a new provisional O-CPID for offline use.
     */
    @Transactional
    public ProvisionalCpidResponse createProvisionalCpid(UUID tenantId, UUID facilityId,
                                                          String deviceFingerprint) {
        UUID oCpid = cpidGenerator.generateProvisionalCpid();

        ProvisionalCpidEntity entity = new ProvisionalCpidEntity();
        entity.setTenantId(tenantId);
        entity.setOriginCpid(oCpid);
        entity.setFacilityId(facilityId);
        entity.setDeviceFingerprint(deviceFingerprint);
        entity.setStatus("PROVISIONAL");

        ProvisionalCpidEntity saved = provisionalRepo.save(entity);

        publishOutboxEvent("ProvisionalCpid", oCpid.toString(), "OCPID_CREATED",
                Map.of("tenantId", tenantId, "oCpid", oCpid, "facilityId", facilityId));

        log.info("Created provisional O-CPID: tenant={}, oCpid={}, facility={}",
                tenantId, oCpid, facilityId);

        return toResponse(saved);
    }

    /**
     * Reconcile an O-CPID to a canonical CPID.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Find the provisional_cpid record</li>
     *   <li>Find-or-create the canonical CPID mapping for (tenantId, healthId)</li>
     *   <li>Update the provisional record with canonical_cpid and status=RECONCILED</li>
     * </ol>
     */
    @Transactional
    public ReconcileResponse reconcile(ReconcileRequest request) {
        ProvisionalCpidEntity provisional = provisionalRepo
                .findByTenantIdAndOriginCpid(request.tenantId(), request.oCpid())
                .orElseThrow(() -> new IdentityNotFoundException(
                        "Provisional CPID not found"));

        if ("RECONCILED".equals(provisional.getStatus())) {
            // Already reconciled — return the existing result
            return new ReconcileResponse(
                    provisional.getOriginCpid(),
                    provisional.getCanonicalCpid(),
                    provisional.getStatus(),
                    provisional.getReconciledAt()
            );
        }

        // The canonical CPID is whatever the id_mapping holds (find-or-create with a
        // fresh random CPID). Never a freshly generated value when a mapping already
        // exists — that would silently fork the patient's clinical key.
        UUID canonicalCpid = idResolutionService
                .findOrCreateMapping(request.tenantId(), request.healthId(), null)
                .getCpid();

        // Update the provisional record
        Instant now = Instant.now();
        provisional.setCanonicalCpid(canonicalCpid);
        provisional.setStatus("RECONCILED");
        provisional.setReconciledAt(now);
        provisionalRepo.save(provisional);

        publishOutboxEvent("ProvisionalCpid", provisional.getOriginCpid().toString(),
                "OCPID_RECONCILED",
                Map.of("tenantId", request.tenantId(),
                       "oCpid", request.oCpid(),
                       "canonicalCpid", canonicalCpid,
                       "healthId", request.healthId()));

        // Clinical-plane repoint signal: O-CPID -> canonical CPID, no health_id
        // (Identity Contract §8 — same repoint machinery as merges downstream).
        subjectEvents.subjectReconciled(request.tenantId(), request.oCpid(), canonicalCpid, null);

        log.info("Reconciled O-CPID {} → canonical CPID {} for tenant={}",
                request.oCpid(), canonicalCpid, request.tenantId());

        return new ReconcileResponse(
                provisional.getOriginCpid(),
                canonicalCpid,
                "RECONCILED",
                now
        );
    }

    /**
     * List all unreconciled O-CPIDs for a tenant.
     */
    @Transactional(readOnly = true)
    public List<ProvisionalCpidResponse> listUnreconciled(UUID tenantId) {
        return provisionalRepo.findByTenantIdAndStatus(tenantId, "PROVISIONAL")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ProvisionalCpidResponse toResponse(ProvisionalCpidEntity entity) {
        return new ProvisionalCpidResponse(
                entity.getOriginCpid(),
                entity.getFacilityId(),
                entity.getDeviceFingerprint(),
                entity.getStatus(),
                entity.getCanonicalCpid(),
                entity.getIssuedAt(),
                entity.getReconciledAt()
        );
    }

    private void publishOutboxEvent(String aggregateType, String aggregateId,
                                     String eventType, Object payload) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            outboxRepo.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", e.getMessage());
        }
    }
}
