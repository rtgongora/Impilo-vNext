package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ResourceEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ResourceRepository extends JpaRepository<ResourceEntity, UUID> {

    @Query("SELECT r FROM ResourceEntity r WHERE r.facility.id = :facilityId AND r.active = true")
    List<ResourceEntity> findByFacilityIdAndActiveTrue(@Param("facilityId") Long facilityId);

    @Query("SELECT r FROM ResourceEntity r WHERE r.facility.id = :facilityId AND r.resourceType = :resourceType AND r.active = true")
    List<ResourceEntity> findByFacilityIdAndResourceTypeAndActiveTrue(@Param("facilityId") Long facilityId, @Param("resourceType") String resourceType);
}
