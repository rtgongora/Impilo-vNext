package zw.gov.mohcc.impilo.pacs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingEditEntity;

import java.util.List;

@Repository
public interface ImagingEditRepository extends JpaRepository<ImagingEditEntity, Long> {
    List<ImagingEditEntity> findByStudyIdAndSeriesIdAndInstanceIdOrderByCreatedAtDesc(Long studyId, Long seriesId, Long instanceId);
    List<ImagingEditEntity> findByStudyIdOrderByCreatedAtDesc(Long studyId);
}
