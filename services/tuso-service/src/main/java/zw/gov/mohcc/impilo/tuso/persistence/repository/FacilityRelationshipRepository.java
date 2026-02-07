package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRelationshipEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface FacilityRelationshipRepository extends JpaRepository<FacilityRelationshipEntity, Long> {

    List<FacilityRelationshipEntity> findBySourceIdAndActiveTrue(Long sourceId);

    List<FacilityRelationshipEntity> findByTargetIdAndActiveTrue(Long targetId);

    List<FacilityRelationshipEntity> findByTenantIdAndRelationshipAndActiveTrue(
            UUID tenantId, String relationship);
}
