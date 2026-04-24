package zw.gov.mohcc.impilo.governance.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.domain.GovernanceEnums;
import zw.gov.mohcc.impilo.governance.persistence.JurisdictionEntity;
import zw.gov.mohcc.impilo.governance.persistence.JurisdictionLinkEntity;
import zw.gov.mohcc.impilo.governance.persistence.JurisdictionLinkRepository;
import zw.gov.mohcc.impilo.governance.persistence.JurisdictionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JurisdictionAdminService {

    private final JurisdictionRepository jurisdictionRepository;
    private final JurisdictionLinkRepository jurisdictionLinkRepository;
    private final GovernanceEventService governanceEventService;

    public JurisdictionAdminService(JurisdictionRepository jurisdictionRepository,
                                    JurisdictionLinkRepository jurisdictionLinkRepository,
                                    GovernanceEventService governanceEventService) {
        this.jurisdictionRepository = jurisdictionRepository;
        this.jurisdictionLinkRepository = jurisdictionLinkRepository;
        this.governanceEventService = governanceEventService;
    }

    @Transactional
    public JurisdictionEntity createJurisdiction(UUID tenantId,
                                                String jurisdictionCode,
                                                String name,
                                                GovernanceEnums.JurisdictionType type,
                                                UUID parentJurisdictionId) {
        TrustContext ctx = TrustContextHolder.require();
        if (jurisdictionRepository.findByTenantIdAndJurisdictionCode(tenantId, jurisdictionCode).isPresent()) {
            throw new IllegalArgumentException("Jurisdiction code already exists");
        }
        JurisdictionEntity j = new JurisdictionEntity(
                UUID.randomUUID(),
                tenantId,
                jurisdictionCode,
                name,
                type.name(),
                GovernanceEnums.JurisdictionStatus.ACTIVE.name());
        j.setParentJurisdictionId(parentJurisdictionId);
        j = jurisdictionRepository.save(j);
        governanceEventService.enqueue("JURISDICTION", j.getId().toString(), "impilo.governance.jurisdiction.created",
                Map.of("jurisdictionId", j.getId().toString(), "code", jurisdictionCode),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return j;
    }

    @Transactional
    public JurisdictionLinkEntity linkTarget(UUID tenantId,
                                              GovernanceEnums.AssignmentTargetType targetType,
                                              String targetId,
                                              UUID jurisdictionId,
                                              String relationshipType,
                                              boolean primary,
                                              LocalDate startDate,
                                              LocalDate endDate) {
        TrustContext ctx = TrustContextHolder.require();
        JurisdictionLinkEntity link = new JurisdictionLinkEntity(
                UUID.randomUUID(),
                tenantId,
                targetType.name(),
                targetId,
                jurisdictionId,
                relationshipType,
                GovernanceEnums.LinkStatus.ACTIVE.name());
        link.setPrimaryFlag(primary);
        link.setStartDate(startDate);
        link.setEndDate(endDate);
        link = jurisdictionLinkRepository.save(link);
        governanceEventService.enqueue("JURISDICTION_LINK", link.getId().toString(), "impilo.governance.jurisdiction.linked",
                Map.of("linkId", link.getId().toString(), "jurisdictionId", jurisdictionId.toString(),
                        "targetType", targetType.name(), "targetId", targetId),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return link;
    }

    public List<JurisdictionEntity> list(UUID tenantId) {
        return jurisdictionRepository.findByTenantIdOrderByNameAsc(tenantId);
    }
}
