package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.clinical.persistence.entity.MultimorbidityDetectionSnapshotEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MultimorbidityDetectionSnapshotRepository
        extends JpaRepository<MultimorbidityDetectionSnapshotEntity, UUID> {

    List<MultimorbidityDetectionSnapshotEntity> findByTenantIdAndSubjectCpidAndStatus(
            String tenantId, String subjectCpid, String status);

    Optional<MultimorbidityDetectionSnapshotEntity> findByTenantIdAndSubjectCpidAndCode(
            String tenantId, String subjectCpid, String code);
}
