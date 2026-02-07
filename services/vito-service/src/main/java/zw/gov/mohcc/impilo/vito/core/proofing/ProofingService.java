package zw.gov.mohcc.impilo.vito.core.proofing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.core.ProofingMethod;
import zw.gov.mohcc.impilo.vito.persistence.entity.ProofingEventEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ProofingEventRepository;

import java.util.List;
import java.util.UUID;

@Service
public class ProofingService {

    private final ProofingEventRepository proofingRepo;

    public ProofingService(ProofingEventRepository proofingRepo) {
        this.proofingRepo = proofingRepo;
    }

    @Transactional
    public ProofingEventEntity recordEvent(UUID tenantId, UUID healthId, ProofingMethod method,
                                            int assuranceLevel, String artifactRefs,
                                            String verifierId, String notes) {
        ProofingEventEntity event = new ProofingEventEntity();
        event.setTenantId(tenantId);
        event.setHealthId(healthId);
        event.setMethod(method);
        event.setAssuranceLevel(assuranceLevel);
        event.setArtifactRefs(artifactRefs != null ? artifactRefs : "[]");
        event.setVerifierId(verifierId);
        event.setStatus("COMPLETED");
        event.setNotes(notes);
        return proofingRepo.save(event);
    }

    @Transactional
    public ProofingEventEntity recordFailedEvent(UUID tenantId, UUID healthId, ProofingMethod method,
                                                   String verifierId, String notes) {
        ProofingEventEntity event = new ProofingEventEntity();
        event.setTenantId(tenantId);
        event.setHealthId(healthId);
        event.setMethod(method);
        event.setAssuranceLevel(0);
        event.setStatus("FAILED");
        event.setVerifierId(verifierId);
        event.setNotes(notes);
        return proofingRepo.save(event);
    }

    /**
     * Compute the highest assurance level achieved for a client.
     */
    @Transactional(readOnly = true)
    public int getMaxAssuranceLevel(UUID tenantId, UUID healthId) {
        List<ProofingEventEntity> events = proofingRepo.findByTenantIdAndHealthIdOrderByCreatedAtDesc(tenantId, healthId);
        return events.stream()
                .filter(e -> "COMPLETED".equals(e.getStatus()))
                .mapToInt(ProofingEventEntity::getAssuranceLevel)
                .max()
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public List<ProofingEventEntity> getHistory(UUID tenantId, UUID healthId) {
        return proofingRepo.findByTenantIdAndHealthIdOrderByCreatedAtDesc(tenantId, healthId);
    }

    @Transactional(readOnly = true)
    public Page<ProofingEventEntity> getHistoryPaged(UUID tenantId, UUID healthId, Pageable pageable) {
        return proofingRepo.findByTenantIdAndHealthId(tenantId, healthId, pageable);
    }
}
