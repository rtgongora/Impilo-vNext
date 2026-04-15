package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ReconciliationActionEntity;

import java.util.List;

@Repository
public interface ReconciliationActionRepository extends JpaRepository<ReconciliationActionEntity, Long> {

    @Query("SELECT a FROM ReconciliationActionEntity a WHERE a.reconciliationCase.id = :caseId ORDER BY a.createdAt DESC")
    List<ReconciliationActionEntity> findByCaseIdOrderByCreatedAtDesc(@Param("caseId") Long caseId);
}
