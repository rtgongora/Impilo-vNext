package zw.gov.mohcc.impilo.support.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.support.domain.CommentEntity;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<CommentEntity, UUID> {
    List<CommentEntity> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
    Page<CommentEntity> findByTicketId(UUID ticketId, Pageable pageable);
}
