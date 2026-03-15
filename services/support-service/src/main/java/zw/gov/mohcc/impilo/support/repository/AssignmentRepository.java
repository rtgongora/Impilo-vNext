package zw.gov.mohcc.impilo.support.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.support.domain.AssignmentEntity;

import java.util.List;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity, UUID> {
    List<AssignmentEntity> findByTicketIdOrderByAssignedAtDesc(UUID ticketId);
    Page<AssignmentEntity> findByTicketId(UUID ticketId, Pageable pageable);
}
