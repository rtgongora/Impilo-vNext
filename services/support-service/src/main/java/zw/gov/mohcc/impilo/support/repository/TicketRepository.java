package zw.gov.mohcc.impilo.support.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.support.domain.TicketEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<TicketEntity, UUID> {
    @Query("SELECT t FROM TicketEntity t WHERE t.tenantId = :tenantId"
            + " AND (:status IS NULL OR t.status = :status)"
            + " AND (:priority IS NULL OR t.priority = :priority)")
    Page<TicketEntity> findFiltered(@Param("tenantId") UUID tenantId,
                                     @Param("status") String status,
                                     @Param("priority") String priority,
                                     Pageable pageable);

    @Query("SELECT t FROM TicketEntity t WHERE t.createdAt <= :asOf")
    Page<TicketEntity> findSnapshotAsOf(@Param("asOf") OffsetDateTime asOf, Pageable pageable);
}
