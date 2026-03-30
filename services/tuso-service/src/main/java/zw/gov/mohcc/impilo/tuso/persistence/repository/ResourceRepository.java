package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ResourceEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ResourceRepository extends JpaRepository<ResourceEntity, UUID> {

    List<ResourceEntity> findByFacility_IdAndActiveTrue(Long facilityId);

    List<ResourceEntity> findByFacility_IdAndResourceTypeAndActiveTrue(Long facilityId, String resourceType);
}
