package zw.gov.mohcc.impilo.governance.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.domain.GovernanceEnums;
import zw.gov.mohcc.impilo.governance.persistence.MultiSiteGroupEntity;
import zw.gov.mohcc.impilo.governance.persistence.MultiSiteGroupMemberEntity;
import zw.gov.mohcc.impilo.governance.persistence.MultiSiteGroupMemberRepository;
import zw.gov.mohcc.impilo.governance.persistence.MultiSiteGroupRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class MultiSiteGroupAdminService {

    private final MultiSiteGroupRepository multiSiteGroupRepository;
    private final MultiSiteGroupMemberRepository multiSiteGroupMemberRepository;
    private final GovernanceEventService governanceEventService;

    public MultiSiteGroupAdminService(MultiSiteGroupRepository multiSiteGroupRepository,
                                      MultiSiteGroupMemberRepository multiSiteGroupMemberRepository,
                                      GovernanceEventService governanceEventService) {
        this.multiSiteGroupRepository = multiSiteGroupRepository;
        this.multiSiteGroupMemberRepository = multiSiteGroupMemberRepository;
        this.governanceEventService = governanceEventService;
    }

    @Transactional
    public MultiSiteGroupEntity createGroup(UUID tenantId, String code, String name, String groupType, UUID organisationId) {
        TrustContext ctx = TrustContextHolder.require();
        if (multiSiteGroupRepository.findByTenantIdAndCode(tenantId, code).isPresent()) {
            throw new IllegalArgumentException("Group code already exists");
        }
        MultiSiteGroupEntity g = new MultiSiteGroupEntity(
                UUID.randomUUID(),
                tenantId,
                code,
                name,
                groupType,
                GovernanceEnums.LinkStatus.ACTIVE.name());
        g.setOrganisationId(organisationId);
        g = multiSiteGroupRepository.save(g);
        governanceEventService.enqueue("MULTI_SITE_GROUP", g.getId().toString(), "impilo.governance.multi_site_group.created",
                Map.of("groupId", g.getId().toString(), "code", code),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return g;
    }

    @Transactional
    public MultiSiteGroupMemberEntity addMember(UUID tenantId, UUID groupId,
                                                GovernanceEnums.AssignmentTargetType memberTargetType,
                                                String memberTargetId,
                                                LocalDate startDate,
                                                LocalDate endDate) {
        TrustContext ctx = TrustContextHolder.require();
        MultiSiteGroupMemberEntity m = new MultiSiteGroupMemberEntity(
                UUID.randomUUID(),
                tenantId,
                groupId,
                memberTargetType.name(),
                memberTargetId,
                GovernanceEnums.LinkStatus.ACTIVE.name());
        m.setStartDate(startDate);
        m.setEndDate(endDate);
        m = multiSiteGroupMemberRepository.save(m);
        governanceEventService.enqueue("MULTI_SITE_GROUP_MEMBER", m.getId().toString(), "impilo.governance.multi_site_group.member_added",
                Map.of("groupId", groupId.toString(), "memberTargetType", memberTargetType.name(), "memberTargetId", memberTargetId),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return m;
    }
}
