package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentAllocationEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocationEntity, Long> {

    boolean existsByCostaPaymentIdAndInvoiceId(Long costaPaymentId, String invoiceId);

    List<PaymentAllocationEntity> findByInvoiceIdOrderByAllocatedAtAsc(String invoiceId);

    List<PaymentAllocationEntity> findByTenantIdAndInvoiceId(UUID tenantId, String invoiceId);
}
