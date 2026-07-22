package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PracticeEstablishmentCaseEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface PracticeEstablishmentCaseRepository extends JpaRepository<PracticeEstablishmentCaseEntity, UUID> {
    List<PracticeEstablishmentCaseEntity> findByTenantIdAndApplicantHealthIdOrderByCreatedAtDesc(UUID tenantId, UUID applicantHealthId);
    List<PracticeEstablishmentCaseEntity> findByTenantIdAndStatus(UUID tenantId, String status);
}
