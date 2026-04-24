package zw.gov.mohcc.impilo.pacs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingStudyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImagingStudyRepository extends JpaRepository<ImagingStudyEntity, Long> {

    Optional<ImagingStudyEntity> findByStudyUid(String studyUid);

    List<ImagingStudyEntity> findByTenantId(UUID tenantId);

    List<ImagingStudyEntity> findByPatientCpid(String patientCpid);

    List<ImagingStudyEntity> findByPatientCpidOrderByStudyDateDesc(String patientCpid);

    List<ImagingStudyEntity> findByStatus(String status);

    Optional<ImagingStudyEntity> findByOrosOrderId(String orosOrderId);

    boolean existsByStudyUid(String studyUid);
}
