package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ApplicationCaseMessageEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationCaseMessageRepository extends JpaRepository<ApplicationCaseMessageEntity, Long> {
    List<ApplicationCaseMessageEntity> findByTenantIdAndApplicationIdOrderByCreatedAtAsc(UUID tenantId, Long applicationId);
    List<ApplicationCaseMessageEntity> findByTenantIdAndApplicationIdAndVisibilityOrderByCreatedAtAsc(UUID tenantId, Long applicationId, String visibility);
}
