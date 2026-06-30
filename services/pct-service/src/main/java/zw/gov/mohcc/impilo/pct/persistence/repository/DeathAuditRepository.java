package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeathAuditEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeathAuditRepository extends JpaRepository<DeathAuditEntity, UUID> {
    List<DeathAuditEntity> findByCaseIdOrderByOccurredAtAsc(UUID caseId);
    long countByCaseId(UUID caseId);
}
