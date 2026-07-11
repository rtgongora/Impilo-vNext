package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityApplicationEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityApplicationState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityApplicationRepository extends JpaRepository<FacilityApplicationEntity, UUID> {
    List<FacilityApplicationEntity> findByFacilityIdOrderByCreatedAtDesc(Long facilityId);
    List<FacilityApplicationEntity> findByTenantIdAndCurrentWorkflowState(UUID tenantId, FacilityApplicationState state);

    @Query("select a from FacilityApplicationEntity a where a.tenantId = :tenantId and a.currentWorkflowState in :states")
    List<FacilityApplicationEntity> findByTenantIdAndStates(@Param("tenantId") UUID tenantId,
                                                            @Param("states") List<FacilityApplicationState> states);

    Optional<FacilityApplicationEntity> findByApplicationNumber(String applicationNumber);
}
