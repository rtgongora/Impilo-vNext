package zw.gov.mohcc.impilo.surgery.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalLongitudinalObjectEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurgicalLongitudinalObjectRepository extends JpaRepository<SurgicalLongitudinalObjectEntity, UUID> {

    Optional<SurgicalLongitudinalObjectEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SurgicalLongitudinalObjectEntity> findBySurgicalEpisodeIdAndTenantIdOrderByPlacedAtDesc(
            UUID surgicalEpisodeId, UUID tenantId);
}
