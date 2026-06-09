package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.FluidBalanceEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FluidBalanceRepository extends JpaRepository<FluidBalanceEntity, UUID> {
    List<FluidBalanceEntity> findByTenantIdAndSubjectCpidAndRecordDateOrderByRecordedAtAsc(
            UUID tenantId, String subjectCpid, LocalDate recordDate);
}
