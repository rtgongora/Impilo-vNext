package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.PaymentEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    List<PaymentEntity> findByBillId(String billId);
    Optional<PaymentEntity> findByMushexPaymentIntentId(String mushexPaymentIntentId);

    @Query("SELECT p FROM PaymentEntity p JOIN BillHeaderEntity b ON p.billId = b.billId WHERE b.tenantId = :tenantId ORDER BY p.createdAt DESC")
    Page<PaymentEntity> findByTenantId(UUID tenantId, Pageable pageable);
}
