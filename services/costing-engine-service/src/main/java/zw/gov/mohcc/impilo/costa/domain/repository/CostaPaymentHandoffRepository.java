package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.CostaPaymentHandoffEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CostaPaymentHandoffRepository extends JpaRepository<CostaPaymentHandoffEntity, UUID> {

    List<CostaPaymentHandoffEntity> findTop30ByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<CostaPaymentHandoffEntity> findByTenantIdAndInvoiceIdIn(UUID tenantId, List<String> invoiceIds);
}
