package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseAttachmentEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaseAttachmentRepository extends JpaRepository<CaseAttachmentEntity, UUID> {

    List<CaseAttachmentEntity> findByCaseId(UUID caseId);
}
