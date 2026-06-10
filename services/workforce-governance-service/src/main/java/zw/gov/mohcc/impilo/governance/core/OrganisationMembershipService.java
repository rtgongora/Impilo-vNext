package zw.gov.mohcc.impilo.governance.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationMembershipEntity;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationMembershipRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrganisationMembershipService {

    private final OrganisationMembershipRepository repository;
    private final GovernanceEventService governanceEventService;

    public OrganisationMembershipService(OrganisationMembershipRepository repository,
                                           GovernanceEventService governanceEventService) {
        this.repository = repository;
        this.governanceEventService = governanceEventService;
    }

    public List<OrganisationMembershipEntity> list(UUID tenantId, UUID organisationId) {
        return repository.findByTenantIdAndOrganisationIdOrderByUpdatedAtDesc(tenantId, organisationId);
    }

    @Transactional
    public OrganisationMembershipEntity addMember(UUID tenantId, UUID organisationId, Map<String, Object> request) {
        String subjectId = str(request.get("userId"));
        String subjectType = strOrDefault(request.get("subjectType"), "USER");
        OrganisationMembershipEntity existing = repository
                .findByTenantIdAndOrganisationIdAndSubjectIdAndSubjectType(tenantId, organisationId, subjectId, subjectType)
                .orElse(null);
        if (existing != null) {
            existing.setRoleTemplate(str(request.get("roleTemplate")));
            existing.setStatus(strOrDefault(request.get("status"), "ACTIVE"));
            existing.setMetadata(str(request.get("metadata")));
            return repository.save(existing);
        }
        OrganisationMembershipEntity entity = new OrganisationMembershipEntity();
        entity.init(tenantId, organisationId, subjectId, subjectType, str(request.get("roleTemplate")), strOrDefault(request.get("status"), "ACTIVE"));
        entity.setMetadata(str(request.get("metadata")));
        entity = repository.save(entity);
        governanceEventService.enqueue("ORG_MEMBERSHIP", entity.getId().toString(), "impilo.governance.organisation.member.added",
                Map.of("organisationId", organisationId.toString(), "subjectId", subjectId), tenantId, null);
        return entity;
    }

    @Transactional
    public OrganisationMembershipEntity updateMember(UUID tenantId, UUID organisationId, String userId, Map<String, Object> request) {
        OrganisationMembershipEntity entity = repository
                .findByTenantIdAndOrganisationIdAndSubjectIdAndSubjectType(tenantId, organisationId, userId, strOrDefault(request.get("subjectType"), "USER"))
                .orElseThrow(() -> new IllegalArgumentException("Organisation member not found"));
        if (request.containsKey("roleTemplate")) entity.setRoleTemplate(str(request.get("roleTemplate")));
        if (request.containsKey("status")) entity.setStatus(str(request.get("status")));
        if (request.containsKey("metadata")) entity.setMetadata(str(request.get("metadata")));
        return repository.save(entity);
    }

    @Transactional
    public OrganisationMembershipEntity suspendMember(UUID tenantId, UUID organisationId, String userId) {
        return updateMember(tenantId, organisationId, userId, Map.of("status", "SUSPENDED"));
    }

    @Transactional
    public OrganisationMembershipEntity offboardMember(UUID tenantId, UUID organisationId, String userId) {
        return updateMember(tenantId, organisationId, userId, Map.of("status", "OFFBOARDED"));
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static String strOrDefault(Object value, String defaultValue) {
        String s = str(value);
        return s == null || s.isBlank() ? defaultValue : s;
    }
}
