package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.core.IdentityStatus;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<ClientEntity, Long> {

    Optional<ClientEntity> findByTenantIdAndHealthId(UUID tenantId, UUID healthId);

    Page<ClientEntity> findByTenantIdAndStatus(UUID tenantId, IdentityStatus status, Pageable pageable);

    Page<ClientEntity> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("SELECT c FROM ClientEntity c WHERE c.tenantId = :tenantId " +
           "AND (:cursor IS NULL OR c.healthId > :cursor) " +
           "ORDER BY c.healthId ASC")
    List<ClientEntity> getSnapshot(
            @Param("tenantId") UUID tenantId,
            @Param("cursor") UUID cursor,
            Pageable pageable);

    boolean existsByTenantIdAndHealthId(UUID tenantId, UUID healthId);

    long countByTenantId(UUID tenantId);

    Optional<ClientEntity> findByTenantIdAndImpiloId(UUID tenantId, String impiloId);

    Optional<ClientEntity> findByTenantIdAndCrid(UUID tenantId, UUID crid);

    @Query("SELECT c FROM ClientEntity c WHERE c.tenantId = :tenantId " +
           "AND (LOWER(c.givenName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(c.familyName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR c.impiloId = :q)")
    Page<ClientEntity> searchByNameOrImpiloId(
            @Param("tenantId") UUID tenantId,
            @Param("q") String query,
            Pageable pageable);
}
