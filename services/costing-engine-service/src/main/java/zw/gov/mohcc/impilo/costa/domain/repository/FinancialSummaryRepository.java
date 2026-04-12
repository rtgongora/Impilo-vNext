package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.FinancialSummaryEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface FinancialSummaryRepository extends JpaRepository<FinancialSummaryEntity, Long> {

    List<FinancialSummaryEntity> findByTenantIdAndFacilityIdAndSummaryType(
            UUID tenantId, UUID facilityId, String summaryType);
}
