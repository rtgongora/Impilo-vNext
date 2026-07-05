package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {

    Optional<OrganizationEntity> findByCode(String code);

    Optional<OrganizationEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<OrganizationEntity> findBySourceAndSourceRef(String source, String sourceRef);

    List<OrganizationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<OrganizationEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
}
