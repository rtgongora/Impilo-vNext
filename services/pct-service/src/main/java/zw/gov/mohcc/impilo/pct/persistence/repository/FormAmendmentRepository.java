package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormAmendmentEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface FormAmendmentRepository extends JpaRepository<FormAmendmentEntity, UUID> {

    List<FormAmendmentEntity> findByResponseIdOrderByAmendedAtDesc(UUID responseId);
}
