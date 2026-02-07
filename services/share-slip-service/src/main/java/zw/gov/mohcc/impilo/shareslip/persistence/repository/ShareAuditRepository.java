package zw.gov.mohcc.impilo.shareslip.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.ShareAuditEntity;

import java.util.UUID;

@Repository
public interface ShareAuditRepository extends JpaRepository<ShareAuditEntity, Long> {

    Page<ShareAuditEntity> findByLinkIdOrderByCreatedAtDesc(UUID linkId, Pageable pageable);
}
