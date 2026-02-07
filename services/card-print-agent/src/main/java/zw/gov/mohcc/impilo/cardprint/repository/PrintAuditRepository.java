package zw.gov.mohcc.impilo.cardprint.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.cardprint.entity.PrintAuditEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrintAuditRepository extends JpaRepository<PrintAuditEntity, Long> {

    List<PrintAuditEntity> findByJobIdOrderByCreatedAtDesc(UUID jobId);
}
