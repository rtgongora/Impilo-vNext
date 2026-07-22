package zw.gov.mohcc.impilo.analyticspipeline.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TelemedicineEventRepository extends JpaRepository<TelemedicineEventEntity, UUID> {

    long countByTenantId(UUID tenantId);

    // TM-B17: cross-tenant aggregate variants — events carry their envelope tenant (no longer the
    // hardcoded default), so the preview aggregates count everything.
    @Query("""
            SELECT e.eventType AS eventType, COUNT(e) AS eventCount
            FROM TelemedicineEventEntity e
            GROUP BY e.eventType
            ORDER BY e.eventType
            """)
    List<EventTypeCount> countGroupedByEventTypeAll();

    List<TelemedicineEventEntity> findByEventTypeStartingWith(String prefix);

    @Query("""
            SELECT e.eventType AS eventType, COUNT(e) AS eventCount
            FROM TelemedicineEventEntity e
            WHERE e.tenantId = :tenantId
            GROUP BY e.eventType
            ORDER BY e.eventType
            """)
    List<EventTypeCount> countGroupedByEventType(@Param("tenantId") UUID tenantId);

    interface EventTypeCount {
        String getEventType();
        long getEventCount();
    }
}
