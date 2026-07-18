package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.core.ClientVerificationState;
import zw.gov.mohcc.impilo.vito.core.IdentityStatus;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<ClientEntity, Long> {

    Optional<ClientEntity> findByTenantIdAndHealthId(UUID tenantId, UUID healthId);

    Page<ClientEntity> findByTenantIdAndStatus(UUID tenantId, IdentityStatus status, Pageable pageable);

    Page<ClientEntity> findByTenantIdAndVerificationStatus(UUID tenantId,
                                                           ClientVerificationState verificationState,
                                                           Pageable pageable);

    Page<ClientEntity> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * Blocking query for probabilistic matching (Identity Contract §13 / E3):
     * return only candidates that share a coarse blocking key with the source —
     * same birth year OR same family-name initial — so the O(N) Jaro-Winkler
     * scan runs over a small candidate set instead of the whole tenant (and
     * without the previous silent 1000-row cap).
     */
    @Query("SELECT c FROM ClientEntity c WHERE c.tenantId = :tenantId "
         + "AND c.healthId <> :sourceHealthId "
         + "AND ((:birthYear IS NOT NULL AND YEAR(c.dateOfBirth) = :birthYear) "
         + "  OR (:familyInitial IS NOT NULL AND LOWER(SUBSTRING(c.familyName, 1, 1)) = :familyInitial))")
    List<ClientEntity> findBlockingCandidates(@Param("tenantId") UUID tenantId,
                                              @Param("sourceHealthId") UUID sourceHealthId,
                                              @Param("birthYear") Integer birthYear,
                                              @Param("familyInitial") String familyInitial);

    List<ClientEntity> findTop100ByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

    boolean existsByTenantIdAndHealthId(UUID tenantId, UUID healthId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, IdentityStatus status);

    long countByTenantIdAndVerificationStatus(UUID tenantId, ClientVerificationState verificationState);

    Optional<ClientEntity> findByTenantIdAndCrid(UUID tenantId, UUID crid);

    // Identity Contract §6: impiloId is encrypted at rest and cannot be matched
    // by value in SQL. Name search stays here; exact Impilo-ID match is resolved
    // separately via the alias vault (see ClientIdentityOperationsService).
    @Query("SELECT c FROM ClientEntity c WHERE c.tenantId = :tenantId " +
           "AND (LOWER(c.givenName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(COALESCE(c.middleName, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(c.familyName) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<ClientEntity> searchByNameOrImpiloId(@Param("tenantId") UUID tenantId,
                                              @Param("q") String query,
                                              Pageable pageable);

    @Query("SELECT c FROM ClientEntity c WHERE c.tenantId = :tenantId " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:verificationState IS NULL OR c.verificationStatus = :verificationState) " +
           "AND (:query IS NULL OR :query = '' OR " +
           "LOWER(c.givenName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(c.middleName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.familyName) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<ClientEntity> searchClients(@Param("tenantId") UUID tenantId,
                                     @Param("query") String query,
                                     @Param("status") IdentityStatus status,
                                     @Param("verificationState") ClientVerificationState verificationState,
                                     Pageable pageable);
}
