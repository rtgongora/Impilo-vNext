package zw.gov.mohcc.impilo.pacs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingStudyExceptionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImagingStudyExceptionRepository extends JpaRepository<ImagingStudyExceptionEntity, Long> {

    List<ImagingStudyExceptionEntity> findTop200ByTenantIdAndStatusOrderByRaisedAtDesc(UUID tenantId, String status);

    List<ImagingStudyExceptionEntity> findTop200ByTenantIdOrderByRaisedAtDesc(UUID tenantId);

    List<ImagingStudyExceptionEntity> findByStudyIdOrderByRaisedAtDesc(Long studyId);

    boolean existsByStudyIdAndExceptionTypeAndStatus(Long studyId, String exceptionType, String status);
}
