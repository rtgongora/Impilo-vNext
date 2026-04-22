package zw.gov.mohcc.impilo.governance.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.domain.GovernanceEnums;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationUnitEntity;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationUnitRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrganisationUnitService {

    private final OrganisationUnitRepository organisationUnitRepository;
    private final GovernanceEventService governanceEventService;

    public OrganisationUnitService(OrganisationUnitRepository organisationUnitRepository,
                                   GovernanceEventService governanceEventService) {
        this.organisationUnitRepository = organisationUnitRepository;
        this.governanceEventService = governanceEventService;
    }

    @Transactional
    public OrganisationUnitEntity createUnit(UUID tenantId,
                                             UUID organisationId,
                                             UUID parentUnitId,
                                             String unitCode,
                                             String name,
                                             String unitType) {
        TrustContext ctx = TrustContextHolder.require();
        OrganisationUnitEntity u = new OrganisationUnitEntity(
                UUID.randomUUID(),
                tenantId,
                organisationId,
                unitCode,
                name,
                unitType,
                GovernanceEnums.OrganisationUnitStatus.ACTIVE.name());
        u.setParentUnitId(parentUnitId);
        u = organisationUnitRepository.save(u);
        governanceEventService.enqueue("ORGANISATION_UNIT", u.getId().toString(), "impilo.governance.organisation_unit.created",
                Map.of("organisationUnitId", u.getId().toString(), "organisationId", organisationId.toString()),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return u;
    }

    public List<OrganisationUnitEntity> listUnits(UUID tenantId, UUID organisationId) {
        return organisationUnitRepository.findByTenantIdAndOrganisationIdOrderByNameAsc(tenantId, organisationId);
    }
}
