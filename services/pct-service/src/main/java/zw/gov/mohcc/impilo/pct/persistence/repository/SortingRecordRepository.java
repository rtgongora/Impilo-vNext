package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.SortingRecordEntity;

import java.util.Optional;
import java.util.UUID;

public interface SortingRecordRepository extends JpaRepository<SortingRecordEntity, Long> {

    Optional<SortingRecordEntity> findFirstByTenantIdAndJourneyIdOrderByCreatedAtDesc(UUID tenantId, String journeyId);
}
