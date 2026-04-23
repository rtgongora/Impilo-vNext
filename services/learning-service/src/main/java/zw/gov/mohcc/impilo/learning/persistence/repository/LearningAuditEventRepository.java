package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningAuditEventEntity;

public interface LearningAuditEventRepository extends JpaRepository<LearningAuditEventEntity, UUID> {}
