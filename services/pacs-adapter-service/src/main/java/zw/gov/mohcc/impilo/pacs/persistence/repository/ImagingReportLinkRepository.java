package zw.gov.mohcc.impilo.pacs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingReportLinkEntity;

import java.util.List;

@Repository
public interface ImagingReportLinkRepository extends JpaRepository<ImagingReportLinkEntity, Long> {

    List<ImagingReportLinkEntity> findByStudyIdOrderByCreatedAtDesc(Long studyId);
}
