package zw.gov.mohcc.impilo.support.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.support.domain.SupportMessageEntity;

import java.util.List;
import java.util.UUID;

public interface SupportMessageRepository extends JpaRepository<SupportMessageEntity, UUID> {
    List<SupportMessageEntity> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
    Page<SupportMessageEntity> findByTicketId(UUID ticketId, Pageable pageable);
}
