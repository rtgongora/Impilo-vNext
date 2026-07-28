package zw.gov.mohcc.impilo.support.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.support.domain.SupportContextAssignmentEntity;

import java.util.List;
import java.util.UUID;

public interface SupportContextAssignmentRepository extends JpaRepository<SupportContextAssignmentEntity, UUID> {
    List<SupportContextAssignmentEntity> findByPersonHealthIdAndStatus(String personHealthId, String status);
    List<SupportContextAssignmentEntity> findByTeamIdAndStatus(UUID teamId, String status);
}
