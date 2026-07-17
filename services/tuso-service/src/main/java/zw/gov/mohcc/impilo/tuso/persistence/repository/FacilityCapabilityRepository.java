package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCapabilityEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface FacilityCapabilityRepository extends JpaRepository<FacilityCapabilityEntity, Long> {

    List<FacilityCapabilityEntity> findByFacilityIdAndActiveTrue(Long facilityId);

    List<FacilityCapabilityEntity> findByFacilityId(Long facilityId);

    List<FacilityCapabilityEntity> findByFacilityIdAndCapabilityType(Long facilityId, String capabilityType);

    List<FacilityCapabilityEntity> findByCapabilityCodeAndCapabilityType(String capabilityCode, String capabilityType);

    /**
     * Facility IDs (tenant-scoped) whose ACTIVE capability matches a free service token —
     * matching either the capability code or the capability display name (case-insensitive
     * substring). This is the system-of-record backing for service-aware "find care" search:
     * a facility appears only if it carries a live, matching capability, never on a guess.
     *
     * @param tenantId tenant isolation scope
     * @param token    lower-cased service token (e.g. {@code radiology}, {@code maternity})
     * @return distinct facility IDs offering the service
     */
    @Query("SELECT DISTINCT c.facility.id FROM FacilityCapabilityEntity c " +
           "WHERE c.tenantId = :tenantId AND c.active = true " +
           "AND (LOWER(c.capabilityCode) LIKE CONCAT('%', :token, '%') " +
           "OR LOWER(c.name) LIKE CONCAT('%', :token, '%'))")
    List<Long> findFacilityIdsByActiveServiceToken(
            @Param("tenantId") UUID tenantId,
            @Param("token") String token);
}
