package zw.gov.mohcc.impilo.tshepo.identity.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.IdMappingEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdMappingRepository extends JpaRepository<IdMappingEntity, Long> {

    Optional<IdMappingEntity> findByTenantIdAndHealthId(UUID tenantId, UUID healthId);

    Optional<IdMappingEntity> findByTenantIdAndCpid(UUID tenantId, UUID cpid);
}
