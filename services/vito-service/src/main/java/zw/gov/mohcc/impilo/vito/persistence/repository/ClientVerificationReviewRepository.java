package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientVerificationReviewEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientVerificationReviewRepository extends JpaRepository<ClientVerificationReviewEntity, UUID> {
    List<ClientVerificationReviewEntity> findByClientHealthIdOrderByCreatedAtDesc(UUID clientHealthId);
    List<ClientVerificationReviewEntity> findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(UUID tenantId, UUID clientHealthId);
    List<ClientVerificationReviewEntity> findTop50ByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
    Optional<ClientVerificationReviewEntity> findByReviewIdAndTenantId(UUID reviewId, UUID tenantId);
}
