package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.msika.persistence.entity.ChangeLogEntity;

public interface ChangeLogRepository extends JpaRepository<ChangeLogEntity, String> {
    Page<ChangeLogEntity> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);
}
