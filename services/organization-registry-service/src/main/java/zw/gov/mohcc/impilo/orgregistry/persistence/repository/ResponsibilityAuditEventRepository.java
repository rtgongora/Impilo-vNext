package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ResponsibilityAuditEventEntity;

import java.util.UUID;

public interface ResponsibilityAuditEventRepository
        extends JpaRepository<ResponsibilityAuditEventEntity, UUID> {

    Page<ResponsibilityAuditEventEntity> findByTargetTypeAndTargetIdOrderByOccurredAtDesc(
            String targetType, UUID targetId, Pageable pageable);
}
