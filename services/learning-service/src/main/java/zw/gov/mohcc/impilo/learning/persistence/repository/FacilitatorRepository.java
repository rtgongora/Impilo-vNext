package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.FacilitatorEntity;

public interface FacilitatorRepository extends JpaRepository<FacilitatorEntity, UUID> {

    Optional<FacilitatorEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<FacilitatorEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<FacilitatorEntity> findByTenantIdAndFacilitatorKindOrderByCreatedAtDesc(
            UUID tenantId, String facilitatorKind, Pageable pageable);
}
