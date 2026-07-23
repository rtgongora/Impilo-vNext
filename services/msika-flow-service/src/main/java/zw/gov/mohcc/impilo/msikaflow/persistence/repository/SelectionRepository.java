package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.SelectionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SelectionRepository extends JpaRepository<SelectionEntity, String> {

    List<SelectionEntity> findByRequestId(String requestId);

    Optional<SelectionEntity> findFirstByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
