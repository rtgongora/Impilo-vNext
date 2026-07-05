package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.SessionAttendanceEntity;

public interface SessionAttendanceRepository extends JpaRepository<SessionAttendanceEntity, UUID> {

    List<SessionAttendanceEntity> findByTenantIdAndSessionId(UUID tenantId, UUID sessionId);

    Optional<SessionAttendanceEntity> findBySessionIdAndSubjectTypeAndSubjectId(
            UUID sessionId, String subjectType, String subjectId);

    List<SessionAttendanceEntity> findBySessionIdInAndSubjectId(List<UUID> sessionIds, String subjectId);

    List<SessionAttendanceEntity> findBySessionId(UUID sessionId);
}
