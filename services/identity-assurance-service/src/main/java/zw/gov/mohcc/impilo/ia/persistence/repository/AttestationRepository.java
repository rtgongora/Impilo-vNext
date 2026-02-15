package zw.gov.mohcc.impilo.ia.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ia.persistence.entity.AttestationEntity;

@Repository
public interface AttestationRepository extends JpaRepository<AttestationEntity, Long> {

    List<AttestationEntity> findByTenantId(UUID tenantId);

    List<AttestationEntity> findByTenantIdAndActorId(UUID tenantId, String actorId);

    List<AttestationEntity> findByTenantIdAndAttestationType(UUID tenantId, zw.gov.mohcc.impilo.ia.core.AttestationType attestationType);
}
