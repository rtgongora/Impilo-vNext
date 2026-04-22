package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientIdentityEvidenceEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientIdentityEvidenceRepository extends JpaRepository<ClientIdentityEvidenceEntity, UUID> {
    List<ClientIdentityEvidenceEntity> findByClientHealthIdOrderByCreatedAtDesc(UUID clientHealthId);
    List<ClientIdentityEvidenceEntity> findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(UUID tenantId, UUID clientHealthId);
}
