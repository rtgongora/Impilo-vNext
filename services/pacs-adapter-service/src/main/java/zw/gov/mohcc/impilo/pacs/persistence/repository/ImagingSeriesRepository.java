package zw.gov.mohcc.impilo.pacs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingSeriesEntity;

import java.util.List;

@Repository
public interface ImagingSeriesRepository extends JpaRepository<ImagingSeriesEntity, Long> {

    List<ImagingSeriesEntity> findByStudy_IdOrderBySeriesNumberAscIdAsc(Long studyId);

    void deleteByStudy_Id(Long studyId);
}
