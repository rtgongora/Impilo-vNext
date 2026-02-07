package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ConfigAuditEntity;

@Repository
public interface ConfigAuditRepository extends JpaRepository<ConfigAuditEntity, Long> {

    Page<ConfigAuditEntity> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId, Pageable pageable);

    Page<ConfigAuditEntity> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);
}
