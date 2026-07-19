package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityGovernanceCaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacilityGovernanceCaseRepository
        extends JpaRepository<FacilityGovernanceCaseEntity, Long> {

    Optional<FacilityGovernanceCaseEntity> findByIdAndTenantId(Long id, UUID tenantId);

    List<FacilityGovernanceCaseEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, String status);

    List<FacilityGovernanceCaseEntity> findByTenantIdAndCaseTypeAndStatusOrderByCreatedAtDesc(
            UUID tenantId, String caseType, String status);
}
