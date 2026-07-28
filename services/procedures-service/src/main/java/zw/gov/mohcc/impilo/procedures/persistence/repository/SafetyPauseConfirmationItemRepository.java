package zw.gov.mohcc.impilo.procedures.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procedures.persistence.entity.SafetyPauseConfirmationItemEntity;

import java.util.List;
import java.util.UUID;

public interface SafetyPauseConfirmationItemRepository extends JpaRepository<SafetyPauseConfirmationItemEntity, UUID> {
    List<SafetyPauseConfirmationItemEntity> findByTemplateIdOrderByDisplayOrderAsc(UUID templateId);
}
