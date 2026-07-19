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

    /** Resolve a facility by its canonical UUID — the identity downstream services (PCT) key off. */
    Optional<FacilityEntity> findByFacilityUuid(UUID facilityUuid);

    Optional<FacilityEntity> findByGofrId(String gofrId);

    /**
     * Facilities with no active workspace — the operational dead-ends targeted by
     * bulk default provisioning. Ordered by id so paced batches are resumable.
     */
    @Query("SELECT f FROM FacilityEntity f WHERE f.tenantId = :tenantId " +
           "AND (:province IS NULL OR f.province = :province) " +
           "AND NOT EXISTS (SELECT 1 FROM WorkspaceEntity w WHERE w.facility = f AND w.active = true) " +
           "ORDER BY f.id")
    Page<FacilityEntity> findWithoutActiveWorkspace(
            @Param("tenantId") UUID tenantId,
            @Param("province") String province,
            Pageable pageable);

    @Query("SELECT f FROM FacilityEntity f WHERE f.tenantId = :tenantId " +
           "AND LOWER(f.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<FacilityEntity> searchByNameContainingIgnoreCase(
            @Param("tenantId") UUID tenantId,
            @Param("name") String name,
            Pageable pageable);

    @Query("SELECT f FROM FacilityEntity f WHERE (:tenantId IS NULL OR f.tenantId = :tenantId) " +
           // CAST(:query AS string) gives the parameter a definite text type even when null — without
           // it a null :query inside LOWER(CONCAT(...)) is sent untyped and Postgres resolves it to
           // `lower(bytea)` (function does not exist) → 500.
           "AND (:query IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))) " +
           "AND (:type IS NULL OR f.facilityType = :type) " +
           "AND (:status IS NULL OR f.status = :status) " +
           "AND (:regulatoryStatus IS NULL OR f.regulatoryStatus = :regulatoryStatus) " +
           "AND (:district IS NULL OR f.district = :district) " +
           "AND (:province IS NULL OR f.province = :province)")
    Page<FacilityEntity> searchByNameAndFilters(
            @Param("tenantId") UUID tenantId,
            @Param("query") String query,
            @Param("type") String type,
            @Param("status") String status,
            @Param("regulatoryStatus") zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus regulatoryStatus,
            @Param("district") String district,
            @Param("province") String province,
            Pageable pageable);

    @Query("SELECT f FROM FacilityEntity f WHERE (:tenantId IS NULL OR f.tenantId = :tenantId) " +
           "AND (:type IS NULL OR f.facilityType = :type) " +
           "AND (:status IS NULL OR f.status = :status) " +
           "AND (:regulatoryStatus IS NULL OR f.regulatoryStatus = :regulatoryStatus) " +
           "AND (:district IS NULL OR f.district = :district) " +
           "AND (:province IS NULL OR f.province = :province)")
    Page<FacilityEntity> findByFilters(
            @Param("tenantId") UUID tenantId,
            @Param("type") String type,
            @Param("status") String status,
            @Param("regulatoryStatus") zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus regulatoryStatus,
            @Param("district") String district,
            @Param("province") String province,
            Pageable pageable);

    /**
     * Facilities restricted to a supplied ID set, intersected with the standard
     * geo/type/status/name filters. Backs service-aware "find care" search: the ID set is
     * the facilities that carry an ACTIVE matching capability (see
     * {@link FacilityCapabilityRepository#findFacilityIdsByActiveServiceToken}), so the
     * result is exactly "facilities that offer this service AND match these filters".
     * Callers must pass a non-empty {@code ids} (an empty {@code IN ()} is invalid SQL).
     */
    @Query("SELECT f FROM FacilityEntity f WHERE (:tenantId IS NULL OR f.tenantId = :tenantId) " +
           "AND f.id IN :ids " +
           // CAST(:query AS string) gives the parameter a definite text type even when null — without
           // it a null :query inside LOWER(CONCAT(...)) is sent untyped and Postgres resolves it to
           // `lower(bytea)` (function does not exist) → 500.
           "AND (:query IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))) " +
           "AND (:type IS NULL OR f.facilityType = :type) " +
           "AND (:status IS NULL OR f.status = :status) " +
           "AND (:district IS NULL OR f.district = :district) " +
           "AND (:province IS NULL OR f.province = :province)")
    Page<FacilityEntity> findByIdInAndFilters(
            @Param("tenantId") UUID tenantId,
            @Param("ids") List<Long> ids,
            @Param("query") String query,
            @Param("type") String type,
            @Param("status") String status,
            @Param("district") String district,
            @Param("province") String province,
            Pageable pageable);

    Page<FacilityEntity> findByTenantId(UUID tenantId, Pageable pageable);

    List<FacilityEntity> findByTenantId(UUID tenantId);

    boolean existsByTenantIdAndFacilityCode(UUID tenantId, String facilityCode);

    long countByTenantId(UUID tenantId);

    Optional<FacilityEntity> findByTenantIdAndGofrId(UUID tenantId, String gofrId);

    /**
     * Live trigram similarity for silent duplicate detection (D-L5; V002 gin_trgm
     * index on name). Each row = [FacilityEntity, similarity score]. Steward
     * material — candidates are never disclosed to registrants.
     */
    @org.springframework.data.jpa.repository.Query(value =
            "SELECT f.*, similarity(lower(f.name), :normalisedName) AS score"
                    + " FROM tuso.facility f"
                    + " WHERE f.tenant_id = :tenantId"
                    + "   AND similarity(lower(f.name), :normalisedName) >= :minScore"
                    + " ORDER BY score DESC LIMIT 5",
            nativeQuery = true)
    List<Object[]> findSimilarByName(
            @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
            @org.springframework.data.repository.query.Param("normalisedName") String normalisedName,
            @org.springframework.data.repository.query.Param("minScore") double minScore);
}
