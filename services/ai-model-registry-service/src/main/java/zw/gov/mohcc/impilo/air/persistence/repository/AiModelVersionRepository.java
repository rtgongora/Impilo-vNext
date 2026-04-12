package zw.gov.mohcc.impilo.air.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.air.persistence.entity.AiModelVersionEntity;

@Repository
public interface AiModelVersionRepository extends JpaRepository<AiModelVersionEntity, Long> {

    List<AiModelVersionEntity> findByModelIdOrderByCreatedAtDesc(UUID modelId);
}
