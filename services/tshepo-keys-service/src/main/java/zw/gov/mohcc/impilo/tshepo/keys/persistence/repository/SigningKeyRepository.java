package zw.gov.mohcc.impilo.tshepo.keys.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.SigningKeyEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SigningKeyRepository extends JpaRepository<SigningKeyEntity, Long> {

    Optional<SigningKeyEntity> findByKeyId(String keyId);

    List<SigningKeyEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<SigningKeyEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /**
     * Find the most recently created ACTIVE key for a given tenant.
     * This is the "current" signing key.
     */
    @Query("SELECT k FROM SigningKeyEntity k WHERE k.tenantId = :tenantId AND k.status = 'ACTIVE' ORDER BY k.createdAt DESC")
    List<SigningKeyEntity> findActiveKeysByTenant(@Param("tenantId") UUID tenantId);

    /**
     * Purpose-scoped active keys for a tenant — the basis of fail-closed,
     * purpose-bound key lookup (most recently created first).
     */
    @Query("SELECT k FROM SigningKeyEntity k WHERE k.tenantId = :tenantId AND k.purpose = :purpose AND k.status = 'ACTIVE' ORDER BY k.createdAt DESC")
    List<SigningKeyEntity> findActiveKeysByTenantAndPurpose(@Param("tenantId") UUID tenantId,
                                                            @Param("purpose") String purpose);

    /**
     * Find all ACTIVE keys across all tenants (used for JWKS endpoint).
     */
    @Query("SELECT k FROM SigningKeyEntity k WHERE k.status = 'ACTIVE' ORDER BY k.createdAt DESC")
    List<SigningKeyEntity> findAllActiveKeys();

    /**
     * Find keys that are due for rotation (ACTIVE and created before the threshold).
     */
    @Query("SELECT k FROM SigningKeyEntity k WHERE k.status = 'ACTIVE' AND k.createdAt < :threshold")
    List<SigningKeyEntity> findKeysNeedingRotation(@Param("threshold") Instant threshold);
}
