package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditSectionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditSectionRepository extends JpaRepository<AuditSectionEntity, UUID> {

    List<AuditSectionEntity> findByAuditToolIdOrderByOrdinalAsc(UUID auditToolId);
}
