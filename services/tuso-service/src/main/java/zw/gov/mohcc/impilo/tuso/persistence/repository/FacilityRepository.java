package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacilityRepository extends JpaRepository<FacilityEntity, Long> {

    Page<FacilityEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    List<FacilityEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    Optional<FacilityEntity> findByTenantIdAndFacilityCode(UUID tenantId, String facilityCode);

    Optional<FacilityEntity> findByGofrId(String gofrId);

    @Query("SELECT f FROM FacilityEntity f WHERE f.tenantId = :tenantId " +
           "AND LOWER(f.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<FacilityEntity> searchByNameContainingIgnoreCase(
            @Param("tenantId") UUID tenantId,
            @Param("name") String name,
            Pageable pageable);

    @Query("SELECT f FROM FacilityEntity f WHERE f.tenantId = :tenantId " +
           "AND (:query IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:type IS NULL OR f.facilityType = :type) " +
           "AND (:status IS NULL OR f.status = :status) " +
           "AND (:district IS NULL OR f.district = :district) " +
           "AND (:province IS NULL OR f.province = :province)")
    Page<FacilityEntity> searchByNameAndFilters(
            @Param("tenantId") UUID tenantId,
            @Param("query") String query,
            @Param("type") String type,
            @Param("status") String status,
            @Param("district") String district,
            @Param("province") String province,
            Pageable pageable);

    @Query("SELECT f FROM FacilityEntity f WHERE f.tenantId = :tenantId " +
           "AND (:type IS NULL OR f.facilityType = :type) " +
           "AND (:status IS NULL OR f.status = :status) " +
           "AND (:district IS NULL OR f.district = :district) " +
           "AND (:province IS NULL OR f.province = :province)")
    Page<FacilityEntity> findByFilters(
            @Param("tenantId") UUID tenantId,
            @Param("type") String type,
            @Param("status") String status,
            @Param("district") String district,
            @Param("province") String province,
            Pageable pageable);

    Page<FacilityEntity> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndFacilityCode(UUID tenantId, String facilityCode);

    Optional<FacilityEntity> findByTenantIdAndGofrId(UUID tenantId, String gofrId);
}
