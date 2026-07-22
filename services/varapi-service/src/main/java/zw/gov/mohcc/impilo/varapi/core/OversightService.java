package zw.gov.mohcc.impilo.varapi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.persistence.entity.OversightEscalationGrantEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.OversightEscalationGrantRepository;

import java.util.List;
import java.util.UUID;

/**
 * HPA oversight (ROM-W10, R8): aggregate reads + explicit per-case escalation grants only. There is
 * NO standing council operational access — a case is visible to HPA only when an ACTIVE grant
 * exists ({@link #oversightCanSeeCase}). Consolidated indicators are aggregate-only (reporting W9).
 */
@Service
public class OversightService {

    private final OversightEscalationGrantRepository grantRepository;

    public OversightService(OversightEscalationGrantRepository grantRepository) {
        this.grantRepository = grantRepository;
    }

    @Transactional
    public OversightEscalationGrantEntity grant(UUID tenantId, Long councilId, String subjectType,
                                                String subjectRef, UUID grantedToOrgId, String reason, String grantedBy) {
        OversightEscalationGrantEntity g = new OversightEscalationGrantEntity();
        g.setTenantId(tenantId);
        g.setCouncilId(councilId);
        g.setSubjectType(subjectType);
        g.setSubjectRef(subjectRef);
        g.setGrantedToOrgId(grantedToOrgId);
        g.setReason(reason);
        g.setGrantedBy(grantedBy);
        g.setStatus("ACTIVE");
        return grantRepository.save(g);
    }

    /** Granted-case-only visibility: HPA sees a council case ONLY with an active grant. */
    @Transactional(readOnly = true)
    public boolean oversightCanSeeCase(UUID tenantId, String subjectType, String subjectRef, UUID oversightOrgId) {
        return grantRepository.existsByTenantIdAndSubjectTypeAndSubjectRefAndGrantedToOrgIdAndStatus(
                tenantId, subjectType, subjectRef, oversightOrgId, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<OversightEscalationGrantEntity> grantsFor(UUID oversightOrgId) {
        return grantRepository.findByGrantedToOrgIdAndStatus(oversightOrgId, "ACTIVE");
    }
}
