package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {

    Optional<OrganizationEntity> findByCode(String code);

    /** Tenant-scoped lookup: a code is unique within a tenant, not across the estate. */
    Optional<OrganizationEntity> findByTenantIdAndCode(UUID tenantId, String code);

    /** Read-only inventory sweep over mirrored rows (source = WGV_MIRROR), paginated. */
    Page<OrganizationEntity> findBySourceOrderByCreatedAtAsc(String source, Pageable pageable);

    Optional<OrganizationEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<OrganizationEntity> findBySourceAndSourceRef(String source, String sourceRef);

    Optional<OrganizationEntity> findByTenantIdAndSourceAndSourceRef(
            UUID tenantId, String source, String sourceRef);

    List<OrganizationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<OrganizationEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
}
