package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.TriageRecordEntity;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link TriageRecordEntity} persistence operations.
 * All finder methods are tenant-scoped to enforce multi-tenancy isolation.
 */
@Repository
public interface TriageRecordRepository extends JpaRepository<TriageRecordEntity, Long> {

    /**
     * Finds all triage records for a given journey within a tenant.
     * A journey may have multiple triage records if re-triaged.
     *
     * @param tenantId  the tenant identifier
     * @param journeyId the journey identifier
     * @return list of triage records for the journey
     */
    List<TriageRecordEntity> findByTenantIdAndJourneyId(UUID tenantId, String journeyId);
}
