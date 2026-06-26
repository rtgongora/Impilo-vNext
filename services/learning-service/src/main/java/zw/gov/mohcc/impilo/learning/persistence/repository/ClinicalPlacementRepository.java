package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.ClinicalPlacementEntity;

public interface ClinicalPlacementRepository extends JpaRepository<ClinicalPlacementEntity, UUID> {

    Optional<ClinicalPlacementEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ClinicalPlacementEntity> findByTenantIdAndStudentProfileIdOrderByCreatedAtDesc(UUID tenantId, UUID studentProfileId);
}
