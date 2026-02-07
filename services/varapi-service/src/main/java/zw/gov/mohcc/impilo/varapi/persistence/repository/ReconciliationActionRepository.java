package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ReconciliationActionEntity;

import java.util.List;

@Repository
public interface ReconciliationActionRepository extends JpaRepository<ReconciliationActionEntity, Long> {

    List<ReconciliationActionEntity> findByCaseIdOrderByCreatedAtDesc(Long caseId);
}
