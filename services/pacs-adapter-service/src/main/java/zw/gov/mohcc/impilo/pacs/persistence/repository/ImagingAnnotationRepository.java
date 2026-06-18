package zw.gov.mohcc.impilo.pacs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingAnnotationEntity;

import java.util.List;

public interface ImagingAnnotationRepository extends JpaRepository<ImagingAnnotationEntity, Long> {

    List<ImagingAnnotationEntity> findByStudyIdOrderByCreatedAtDesc(Long studyId);
}
