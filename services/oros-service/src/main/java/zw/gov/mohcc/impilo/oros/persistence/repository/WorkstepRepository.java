package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.domain.WorkstepStatus;
import zw.gov.mohcc.impilo.oros.domain.WorkstepType;
import zw.gov.mohcc.impilo.oros.persistence.entity.WorkstepEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkstepRepository extends JpaRepository<WorkstepEntity, UUID> {

    List<WorkstepEntity> findByOrderIdOrderByCreatedAtAsc(String orderId);

    Optional<WorkstepEntity> findFirstByOrderIdAndStatusOrderByCreatedAtAsc(
            String orderId, WorkstepStatus status);

    /**
     * Finds a workstep by order ID and step type.
     *
     * @param orderId  the ULID order identifier
     * @param stepType the workstep type
     * @return the matching workstep if found
     */
    Optional<WorkstepEntity> findByOrderIdAndStepType(String orderId, WorkstepType stepType);
}
