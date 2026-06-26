package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.TrainingVenueEntity;

public interface TrainingVenueRepository extends JpaRepository<TrainingVenueEntity, UUID> {

    Optional<TrainingVenueEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<TrainingVenueEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
