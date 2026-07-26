package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulatorBootstrapRequestRepository
        extends JpaRepository<RegulatorBootstrapRequestEntity, UUID> {

    Optional<RegulatorBootstrapRequestEntity> findByTenantIdAndPublicId(UUID tenantId, String publicId);

    Optional<RegulatorBootstrapRequestEntity> findByAccessRequestId(UUID accessRequestId);

    List<RegulatorBootstrapRequestEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, String status);

    /**
     * The live-request guard: a SUBMITTED or PENDING_SECOND_APPROVAL request already holds the
     * lane for this organisation. Checked in the service so a second submitter gets an explanation
     * rather than a raw unique-index violation.
     */
    boolean existsByTenantIdAndOrganizationIdAndStatusIn(
            UUID tenantId, UUID organizationId, List<String> statuses);
}
