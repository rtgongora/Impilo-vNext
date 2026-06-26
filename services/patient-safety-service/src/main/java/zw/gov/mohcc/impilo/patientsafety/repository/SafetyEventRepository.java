package zw.gov.mohcc.impilo.patientsafety.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.patientsafety.domain.SafetyEventEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SafetyEventRepository extends JpaRepository<SafetyEventEntity, UUID> {
    List<SafetyEventEntity> findByReportIdOrderByCreatedAtAsc(UUID reportId);
}
