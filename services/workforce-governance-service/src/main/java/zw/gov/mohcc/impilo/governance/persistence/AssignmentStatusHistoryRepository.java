package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssignmentStatusHistoryRepository extends JpaRepository<AssignmentStatusHistoryEntity, UUID> {

    List<AssignmentStatusHistoryEntity> findByAssignmentIdOrderByChangedAtDesc(UUID assignmentId);
}
