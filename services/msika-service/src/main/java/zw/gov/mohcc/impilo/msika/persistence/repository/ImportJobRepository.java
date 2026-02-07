package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.msika.persistence.entity.ImportJobEntity;

import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJobEntity, String> {
    Page<ImportJobEntity> findByTenantId(UUID tenantId, Pageable pageable);
    Page<ImportJobEntity> findByStatus(String status, Pageable pageable);
}
