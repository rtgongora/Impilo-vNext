package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.experience.domain.Condition;

import java.util.List;
import java.util.UUID;

public interface ConditionRepository extends JpaRepository<Condition, UUID> {

    List<Condition> findByEncounterId(UUID encounterId);

    Page<Condition> findByTenantIdAndPatientId(String tenantId, UUID patientId, Pageable pageable);
}
