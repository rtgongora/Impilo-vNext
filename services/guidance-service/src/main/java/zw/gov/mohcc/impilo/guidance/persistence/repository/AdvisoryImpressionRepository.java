package zw.gov.mohcc.impilo.guidance.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.guidance.persistence.entity.AdvisoryImpressionEntity;

import java.util.UUID;

public interface AdvisoryImpressionRepository extends JpaRepository<AdvisoryImpressionEntity, UUID> {

    long countByAdvisoryIdAndEvent(UUID advisoryId, String event);
}
