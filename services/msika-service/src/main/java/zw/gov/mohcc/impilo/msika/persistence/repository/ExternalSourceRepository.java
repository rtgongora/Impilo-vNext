package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.msika.persistence.entity.ExternalSourceEntity;

import java.util.List;
import java.util.UUID;

public interface ExternalSourceRepository extends JpaRepository<ExternalSourceEntity, String> {
    List<ExternalSourceEntity> findByTenantId(UUID tenantId);
    List<ExternalSourceEntity> findByTenantIdAndEnabled(UUID tenantId, boolean enabled);
}
