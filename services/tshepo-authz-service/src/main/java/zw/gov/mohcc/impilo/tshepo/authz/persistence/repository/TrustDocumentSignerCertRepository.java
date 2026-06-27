package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TrustDocumentSignerCertEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrustDocumentSignerCertRepository extends JpaRepository<TrustDocumentSignerCertEntity, UUID> {

    List<TrustDocumentSignerCertEntity> findByTrustAuthorityIdAndTenantIdOrderByCreatedAtDesc(UUID trustAuthorityId, UUID tenantId);
}
