package zw.gov.mohcc.impilo.patientsafety.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.patientsafety.domain.AttachmentRefEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttachmentRefRepository extends JpaRepository<AttachmentRefEntity, UUID> {
    List<AttachmentRefEntity> findByReportIdOrderByCreatedAtAsc(UUID reportId);
}
