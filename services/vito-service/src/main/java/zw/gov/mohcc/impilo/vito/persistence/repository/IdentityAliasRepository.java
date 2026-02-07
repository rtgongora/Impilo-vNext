package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.IdentityAliasEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdentityAliasRepository extends JpaRepository<IdentityAliasEntity, Long> {

    Optional<IdentityAliasEntity> findByTenantIdAndAliasTypeAndLookupHashAndStatus(
            UUID tenantId, String aliasType, String lookupHash, String status);

    List<IdentityAliasEntity> findByTenantIdAndHealthIdAndStatus(
            UUID tenantId, UUID healthId, String status);

    List<IdentityAliasEntity> findByTenantIdAndHealthId(UUID tenantId, UUID healthId);
}
