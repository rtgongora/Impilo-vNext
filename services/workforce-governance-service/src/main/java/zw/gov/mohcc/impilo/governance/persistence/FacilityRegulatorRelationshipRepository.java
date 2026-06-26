package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityRegulatorRelationshipRepository
        extends JpaRepository<FacilityRegulatorRelationshipEntity, UUID> {

    List<FacilityRegulatorRelationshipEntity> findByTenantIdAndFacilityIdOrderByCreatedAtAsc(
            UUID tenantId, String facilityId);

    List<FacilityRegulatorRelationshipEntity> findByTenantIdAndCouncilIdOrderByCreatedAtAsc(
            UUID tenantId, String councilId);

    Optional<FacilityRegulatorRelationshipEntity>
            findByTenantIdAndFacilityIdAndCouncilIdAndRelationshipType(
            UUID tenantId, String facilityId, String councilId, String relationshipType);

    Optional<FacilityRegulatorRelationshipEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
