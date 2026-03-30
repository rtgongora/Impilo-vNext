package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.experience.domain.QueueEntry;

import java.util.Optional;
import java.util.UUID;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {

    @Query("""
        SELECT q FROM QueueEntry q
        WHERE q.tenantId = :tenantId
        AND (:facilityId IS NULL OR q.facilityId = :facilityId)
        AND (:status IS NULL OR q.status = :status)
        AND (:queueType IS NULL OR q.queueType = :queueType)
        """)
    Page<QueueEntry> findByFilters(
            @Param("tenantId") String tenantId,
            @Param("facilityId") UUID facilityId,
            @Param("status") String status,
            @Param("queueType") String queueType,
            Pageable pageable);

    Optional<QueueEntry> findByIdAndTenantId(UUID id, String tenantId);
}
