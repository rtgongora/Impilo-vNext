package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.clinical.persistence.entity.ClinicalEventOutboxEntity;

import java.util.List;
import java.util.UUID;

public interface ClinicalEventOutboxRepository extends JpaRepository<ClinicalEventOutboxEntity, UUID> {

    List<ClinicalEventOutboxEntity> findTop100ByPublishedAtIsNullOrderByOccurredAtAsc();
}
