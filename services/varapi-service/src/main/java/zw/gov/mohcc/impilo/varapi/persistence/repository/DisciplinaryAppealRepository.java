package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.DisciplinaryAppealEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface DisciplinaryAppealRepository extends JpaRepository<DisciplinaryAppealEntity, Long> {
    List<DisciplinaryAppealEntity> findByTenantIdAndCaseId(UUID tenantId, Long caseId);
}
