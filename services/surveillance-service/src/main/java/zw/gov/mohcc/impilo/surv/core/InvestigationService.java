package zw.gov.mohcc.impilo.surv.core;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.InvestigationEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.CaseRepository;
import zw.gov.mohcc.impilo.surv.persistence.repository.InvestigationRepository;

@Service
public class InvestigationService {

    private final InvestigationRepository investigationRepository;
    private final CaseRepository caseRepository;

    public InvestigationService(InvestigationRepository investigationRepository, CaseRepository caseRepository) {
        this.investigationRepository = investigationRepository;
        this.caseRepository = caseRepository;
    }

    @Transactional(readOnly = true)
    public List<InvestigationEntity> listInvestigations(UUID tenantId) {
        return investigationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public InvestigationEntity openInvestigation(
            UUID tenantId,
            String actorId,
            String title,
            Long caseId,
            Long outbreakId,
            UUID facilityId,
            String assignedTo) {

        InvestigationEntity investigation = new InvestigationEntity();
        investigation.setTenantId(tenantId);
        investigation.setTitle(title != null && !title.isBlank() ? title : "Investigation");
        investigation.setCaseId(caseId);
        investigation.setOutbreakId(outbreakId);
        investigation.setFacilityId(facilityId);
        investigation.setAssignedTo(assignedTo);
        investigation.setStatus(InvestigationStatus.OPEN);
        investigation.setCreatedBy(actorId);

        if (caseId != null) {
            caseRepository.findById(caseId)
                    .filter(c -> tenantId.equals(c.getTenantId()))
                    .ifPresent(c -> {
                        c.setStatus(CaseStatus.INVESTIGATING);
                        caseRepository.save(c);
                    });
        }

        return investigationRepository.save(investigation);
    }

    @Transactional
    public InvestigationEntity updateStatus(UUID tenantId, Long investigationId, InvestigationStatus status, String findings) {
        InvestigationEntity investigation = investigationRepository.findById(investigationId)
                .filter(i -> tenantId.equals(i.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Investigation not found"));
        investigation.setStatus(status);
        if (findings != null) {
            investigation.setFindings(findings);
        }
        if (status == InvestigationStatus.CLOSED && investigation.getCaseId() != null) {
            caseRepository.findById(investigation.getCaseId())
                    .filter(c -> tenantId.equals(c.getTenantId()))
                    .ifPresent(c -> {
                        c.setStatus(CaseStatus.CLOSED);
                        caseRepository.save(c);
                    });
        }
        return investigationRepository.save(investigation);
    }
}
