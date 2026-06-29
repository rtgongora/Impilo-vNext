package zw.gov.mohcc.impilo.guidance.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.guidance.persistence.entity.NompiloHandoffEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NompiloHandoffRepository extends JpaRepository<NompiloHandoffEntity, UUID> {

    Optional<NompiloHandoffEntity> findByIdAndTenantId(UUID id, String tenantId);

    List<NompiloHandoffEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /** The operations-desk queue: open handoffs (not closed/cancelled), oldest first. */
    List<NompiloHandoffEntity> findByTenantIdAndStatusInOrderByCreatedAtAsc(String tenantId, List<String> statuses);
}
