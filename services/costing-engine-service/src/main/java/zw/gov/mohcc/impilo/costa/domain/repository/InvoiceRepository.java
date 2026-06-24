package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.InvoiceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<InvoiceEntity, String> {
    Optional<InvoiceEntity> findByBillId(String billId);

    /** Facility-scoped invoices (InvoiceEntity has no facilityId — joined via the bill). */
    @Query("SELECT i FROM InvoiceEntity i JOIN BillHeaderEntity b ON i.billId = b.billId "
            + "WHERE i.tenantId = :tenantId AND b.facilityId = :facilityId "
            + "ORDER BY i.issuedAt DESC")
    List<InvoiceEntity> findByFacility(UUID tenantId, UUID facilityId, Pageable pageable);
}
