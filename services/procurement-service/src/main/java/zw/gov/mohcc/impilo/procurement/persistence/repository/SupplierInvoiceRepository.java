package zw.gov.mohcc.impilo.procurement.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procurement.persistence.entity.SupplierInvoiceEntity;

import java.util.List;
import java.util.UUID;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoiceEntity, UUID> {
    List<SupplierInvoiceEntity> findByTenantIdOrderByDueDateAsc(UUID tenantId);
}
