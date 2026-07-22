package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.DisciplinaryHearingEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface DisciplinaryHearingRepository extends JpaRepository<DisciplinaryHearingEntity, Long> {
    List<DisciplinaryHearingEntity> findByTenantIdAndCaseId(UUID tenantId, Long caseId);
}
