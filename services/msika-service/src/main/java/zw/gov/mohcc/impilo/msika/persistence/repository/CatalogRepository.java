package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogRepository extends JpaRepository<CatalogEntity, String> {
    Page<CatalogEntity> findByTenantIdOrTenantIdIsNull(UUID tenantId, Pageable pageable);
    Page<CatalogEntity> findByScope(String scope, Pageable pageable);
    List<CatalogEntity> findByNameAndTenantId(String name, UUID tenantId);
    Optional<CatalogEntity> findByNameAndVersionAndTenantId(String name, String version, UUID tenantId);
    List<CatalogEntity> findByStatusAndScope(String status, String scope);
    Optional<CatalogEntity> findFirstByNameAndScopeOrderByVersionDesc(String name, String scope);
}
