package zw.gov.mohcc.impilo.patientsafety.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.patientsafety.domain.E2bExportAttemptEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface E2bExportAttemptRepository extends JpaRepository<E2bExportAttemptEntity, UUID> {
    List<E2bExportAttemptEntity> findByCaseIdOrderByCreatedAtDesc(UUID caseId);
}
