package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegistrationCaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacilityRegistrationCaseRepository
        extends JpaRepository<FacilityRegistrationCaseEntity, Long> {

    Optional<FacilityRegistrationCaseEntity> findByTenantIdAndCaseRef(UUID tenantId, String caseRef);

    List<FacilityRegistrationCaseEntity> findByTenantIdAndOutcomeOrderByCreatedAtDesc(
            UUID tenantId, String outcome);

    List<FacilityRegistrationCaseEntity> findByTenantIdAndApplicantHealthIdOrderByCreatedAtDesc(
            UUID tenantId, String applicantHealthId);
}
