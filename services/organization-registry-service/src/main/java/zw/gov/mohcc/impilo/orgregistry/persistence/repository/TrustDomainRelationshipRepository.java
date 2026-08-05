package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.TrustDomainRelationshipEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrustDomainRelationshipRepository
        extends JpaRepository<TrustDomainRelationshipEntity, UUID> {

    List<TrustDomainRelationshipEntity> findByParentTrustDomainIdAndStatus(UUID parentId, String status);

    Optional<TrustDomainRelationshipEntity> findByChildTrustDomainIdAndStatus(UUID childId, String status);
}
