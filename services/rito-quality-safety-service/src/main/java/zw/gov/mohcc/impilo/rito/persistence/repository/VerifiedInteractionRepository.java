package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.VerifiedInteractionEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerifiedInteractionRepository extends JpaRepository<VerifiedInteractionEntity, UUID> {

    Optional<VerifiedInteractionEntity> findByTenantIdAndEncounterRef(UUID tenantId, String encounterRef);

    boolean existsByTenantIdAndEncounterRef(UUID tenantId, String encounterRef);
}
