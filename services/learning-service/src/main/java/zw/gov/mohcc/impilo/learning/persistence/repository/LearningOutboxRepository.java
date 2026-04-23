package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningOutboxEntity;

public interface LearningOutboxRepository extends JpaRepository<LearningOutboxEntity, UUID> {}
