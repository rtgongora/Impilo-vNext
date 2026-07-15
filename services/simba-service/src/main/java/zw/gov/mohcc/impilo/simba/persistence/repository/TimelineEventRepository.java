package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.TimelineEventEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface TimelineEventRepository extends JpaRepository<TimelineEventEntity, Long> {

    /** First page (no cursor): newest first, keyset-ordered by id DESC. */
    @Query("""
            SELECT e FROM TimelineEventEntity e
            WHERE e.tenantId = :tenantId AND e.personCpid = :cpid
            ORDER BY e.id DESC
            """)
    List<TimelineEventEntity> firstPage(@Param("tenantId") UUID tenantId,
                                        @Param("cpid") String cpid,
                                        Pageable pageable);

    /** Subsequent pages: rows strictly older than the cursor id. */
    @Query("""
            SELECT e FROM TimelineEventEntity e
            WHERE e.tenantId = :tenantId AND e.personCpid = :cpid AND e.id < :cursorId
            ORDER BY e.id DESC
            """)
    List<TimelineEventEntity> afterCursor(@Param("tenantId") UUID tenantId,
                                          @Param("cpid") String cpid,
                                          @Param("cursorId") Long cursorId,
                                          Pageable pageable);
}
