package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.experience.domain.MarketplaceOrder;

import java.util.Optional;
import java.util.UUID;

public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, UUID> {

    Page<MarketplaceOrder> findByTenantIdAndFacilityId(String tenantId, String facilityId, Pageable pageable);

    Optional<MarketplaceOrder> findByIdAndTenantId(UUID id, String tenantId);
}
