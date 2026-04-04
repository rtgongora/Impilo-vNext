package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.ClaimPackEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClaimPackRepository extends JpaRepository<ClaimPackEntity, Long> {
    List<ClaimPackEntity> findByBillId(String billId);

    @Query("SELECT c FROM ClaimPackEntity c JOIN BillHeaderEntity b ON c.billId = b.billId WHERE b.tenantId = :tenantId ORDER BY c.createdAt DESC")
    Page<ClaimPackEntity> findByTenantId(UUID tenantId, Pageable pageable);
}
