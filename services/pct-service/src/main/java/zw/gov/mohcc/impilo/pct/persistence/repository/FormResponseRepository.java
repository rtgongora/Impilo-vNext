package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormResponseRepository extends JpaRepository<FormResponseEntity, UUID> {

    Optional<FormResponseEntity> findByTenantIdAndResponseId(UUID tenantId, UUID responseId);

    List<FormResponseEntity> findByTenantIdAndEncounterIdOrderByCreatedAtDesc(UUID tenantId, String encounterId);

    List<FormResponseEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
