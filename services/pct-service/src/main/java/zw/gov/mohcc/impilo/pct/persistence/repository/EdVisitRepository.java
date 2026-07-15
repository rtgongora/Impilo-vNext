package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.EdVisitEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EdVisitRepository extends JpaRepository<EdVisitEntity, UUID> {

    /** VITO merge repoint: move the ED visit's patient anchor to the surviving Health ID. */
    @Modifying
    @Query("update EdVisitEntity v set v.patientCpid = :survivor "
            + "where v.tenantId = :tenantId and v.patientCpid = :merged")
    int repointPatientCpid(@Param("tenantId") UUID tenantId,
                           @Param("merged") String mergedHealthId,
                           @Param("survivor") String survivorHealthId);

    Optional<EdVisitEntity> findByJourneyId(String journeyId);
    List<EdVisitEntity> findByTenantIdAndFacilityIdAndStatusInOrderByCreatedAtDesc(
            UUID tenantId, UUID facilityId, List<String> statuses);
    List<EdVisitEntity> findByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);
    Optional<EdVisitEntity> findFirstByTenantIdAndTraumaEpisodeIdOrderByCreatedAtDesc(
            UUID tenantId, UUID traumaEpisodeId);
    List<EdVisitEntity> findByTenantIdAndFacilityIdAndStatusOrderByPreArrivalAtDesc(
            UUID tenantId, UUID facilityId, String status);
}
