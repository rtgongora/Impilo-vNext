package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.ProofingEventEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProofingEventRepository extends JpaRepository<ProofingEventEntity, Long> {

    List<ProofingEventEntity> findByTenantIdAndHealthIdOrderByCreatedAtDesc(UUID tenantId, UUID healthId);

    Page<ProofingEventEntity> findByTenantIdAndHealthId(UUID tenantId, UUID healthId, Pageable pageable);
}
