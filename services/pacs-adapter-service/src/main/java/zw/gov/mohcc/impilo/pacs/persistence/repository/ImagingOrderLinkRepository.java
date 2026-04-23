package zw.gov.mohcc.impilo.pacs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingOrderLinkEntity;

import java.util.List;

@Repository
public interface ImagingOrderLinkRepository extends JpaRepository<ImagingOrderLinkEntity, Long> {

    List<ImagingOrderLinkEntity> findByStudyIdOrderByCreatedAtDesc(Long studyId);
}
