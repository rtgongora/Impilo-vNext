package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.FundoPathwayItemEntity;

public interface FundoPathwayItemRepository extends JpaRepository<FundoPathwayItemEntity, UUID> {
    List<FundoPathwayItemEntity> findByPathwayIdOrderBySequenceNoAsc(UUID pathwayId);
}
