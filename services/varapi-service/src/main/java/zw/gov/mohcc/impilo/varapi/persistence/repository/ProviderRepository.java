package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderRepository extends JpaRepository<ProviderEntity, Long> {

    Optional<ProviderEntity> findByProviderPublicId(String providerPublicId);

    Optional<ProviderEntity> findByProviderPublicIdAndTenantId(String providerPublicId, UUID tenantId);

    Optional<ProviderEntity> findByIdAndTenantId(Long id, UUID tenantId);

    Page<ProviderEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    List<ProviderEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    @Query("SELECT p FROM ProviderEntity p WHERE p.tenantId = :tenantId " +
           "AND (LOWER(p.givenName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(p.familyName) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<ProviderEntity> searchByName(@Param("tenantId") UUID tenantId,
                                      @Param("query") String query,
                                      Pageable pageable);

    Page<ProviderEntity> findByTenantIdAndProfession(UUID tenantId, String profession, Pageable pageable);

    List<ProviderEntity> findByStatus(String status);

    Page<ProviderEntity> findByStatus(String status, Pageable pageable);

    Optional<ProviderEntity> findByTenantIdAndImpiloHealthId(UUID tenantId, UUID impiloHealthId);

    /** HAR W3 — resolve a council registration number to a preloaded profile (reviewer-facing only). */
    Optional<ProviderEntity> findFirstByTenantIdAndPracticeNumberIgnoreCase(UUID tenantId, String practiceNumber);
}
