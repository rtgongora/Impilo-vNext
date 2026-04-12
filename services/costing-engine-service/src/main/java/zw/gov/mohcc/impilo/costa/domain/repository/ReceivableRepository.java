package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.ReceivableEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceivableRepository extends JpaRepository<ReceivableEntity, Long> {

    List<ReceivableEntity> findByTenantIdAndBillIdOrderByCreatedAtAsc(UUID tenantId, String billId);

    Optional<ReceivableEntity> findByReceivableId(UUID receivableId);

    @Query("select r from ReceivableEntity r where r.tenantId = :tenantId "
            + "and (:facilityId is null or r.facilityId = :facilityId) "
            + "and (:debtorRef is null or r.debtorRef = :debtorRef) "
            + "and (:debtorType is null or r.debtorType = :debtorType) "
            + "and (:status is null or r.status = :status) "
            + "and (:agingBucket is null or r.agingBucket = :agingBucket) "
            + "and (:outstandingOnly = false or r.outstanding > 0) "
            + "order by r.dueDate asc, r.createdAt asc")
    Page<ReceivableEntity> search(
            @Param("tenantId") UUID tenantId,
            @Param("facilityId") UUID facilityId,
            @Param("debtorRef") String debtorRef,
            @Param("debtorType") String debtorType,
            @Param("status") String status,
            @Param("agingBucket") String agingBucket,
            @Param("outstandingOnly") boolean outstandingOnly,
            Pageable pageable);

    List<ReceivableEntity> findByTenantIdAndDebtorRefAndDebtorType(UUID tenantId, String debtorRef, String debtorType);
}
