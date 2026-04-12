package zw.gov.mohcc.impilo.coverage.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.UtilizationSummaryEntity;

@Repository
public interface UtilizationSummaryRepository extends JpaRepository<UtilizationSummaryEntity, Long> {

    Optional<UtilizationSummaryEntity> findByTenantIdAndMemberCpidAndPlanCodeAndBenefitCodeAndPeriodStart(
            UUID tenantId, String memberCpid, String planCode, String benefitCode, LocalDate periodStart);
}
