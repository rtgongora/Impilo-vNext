package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.TransferEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link TransferEntity} persistence operations.
 * All finder methods are tenant-scoped to enforce multi-tenancy isolation.
 */
@Repository
public interface TransferRepository extends JpaRepository<TransferEntity, UUID> {

    /**
     * Finds a specific transfer by tenant and transfer ID.
     *
     * @param tenantId   the tenant identifier
     * @param transferId the transfer identifier
     * @return the transfer if found
     */
    Optional<TransferEntity> findByTenantIdAndTransferId(UUID tenantId, UUID transferId);

    /**
     * Finds all transfers for a given journey within a tenant.
     *
     * @param tenantId  the tenant identifier
     * @param journeyId the journey identifier
     * @return list of transfers for the journey
     */
    List<TransferEntity> findByTenantIdAndJourneyId(UUID tenantId, String journeyId);

    /**
     * Finds all transfers with a given status within a tenant.
     *
     * @param tenantId the tenant identifier
     * @param status   the transfer status to filter by
     * @return list of matching transfers
     */
    List<TransferEntity> findByTenantIdAndStatus(UUID tenantId, String status);
}
