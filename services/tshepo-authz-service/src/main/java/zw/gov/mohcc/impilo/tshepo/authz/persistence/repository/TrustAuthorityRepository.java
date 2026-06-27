package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TrustAuthorityEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrustAuthorityRepository extends JpaRepository<TrustAuthorityEntity, UUID> {

    List<TrustAuthorityEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<TrustAuthorityEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
