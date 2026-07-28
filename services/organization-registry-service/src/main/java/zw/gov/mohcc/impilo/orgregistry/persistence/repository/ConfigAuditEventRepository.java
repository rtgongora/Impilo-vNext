package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ConfigAuditEventEntity;

import java.util.List;
import java.util.UUID;

public interface ConfigAuditEventRepository extends JpaRepository<ConfigAuditEventEntity, UUID> {

    List<ConfigAuditEventEntity> findByTenantIdAndSubjectTypeAndSubjectIdOrderByOccurredAtDesc(
            UUID tenantId, String subjectType, UUID subjectId);
}
