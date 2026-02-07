package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ResourceEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ResourceRepository extends JpaRepository<ResourceEntity, UUID> {

    List<ResourceEntity> findByFacilityIdAndActiveTrue(Long facilityId);

    List<ResourceEntity> findByFacilityIdAndResourceTypeAndActiveTrue(Long facilityId, String resourceType);
}
