package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.SiteRegistrationCaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteRegistrationCaseRepository extends JpaRepository<SiteRegistrationCaseEntity, Long> {

    Optional<SiteRegistrationCaseEntity> findByTenantIdAndCaseRef(UUID tenantId, String caseRef);

    List<SiteRegistrationCaseEntity> findByTenantIdAndOutcomeOrderByCreatedAtDesc(
            UUID tenantId, String outcome);
}
