package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.clinical.persistence.entity.ClinicalKnowledgeItemEntity;

import java.util.UUID;

public interface ClinicalKnowledgeItemRepository extends JpaRepository<ClinicalKnowledgeItemEntity, UUID> {
}
