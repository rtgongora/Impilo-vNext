package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.CatalogItemEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CatalogItemRepository extends JpaRepository<CatalogItemEntity, UUID> {

    Page<CatalogItemEntity> findByTenantIdAndOrderTypeAndActiveTrue(
            UUID tenantId, OrderType orderType, Pageable pageable);

    Page<CatalogItemEntity> findByTenantIdAndActiveTrue(UUID tenantId, Pageable pageable);

    Optional<CatalogItemEntity> findByTenantIdAndCode(UUID tenantId, String code);
}
