package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.ProductEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
    List<ProductEntity> findByTenantIdAndSchemeRef(UUID tenantId, UUID schemeRef);
    Optional<ProductEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<ProductEntity> findByTenantIdAndProductCode(UUID tenantId, String productCode);
}
