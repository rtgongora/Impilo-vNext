package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.InspectionChecklistItemEntity;

import java.util.List;
import java.util.UUID;

public interface InspectionChecklistItemRepository extends JpaRepository<InspectionChecklistItemEntity, UUID> {
    List<InspectionChecklistItemEntity> findByTemplateIdOrderByDisplayOrderAsc(UUID templateId);
}

