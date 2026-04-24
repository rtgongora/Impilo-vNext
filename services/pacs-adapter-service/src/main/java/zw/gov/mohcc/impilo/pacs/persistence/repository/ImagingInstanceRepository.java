package zw.gov.mohcc.impilo.pacs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingInstanceEntity;

import java.util.List;

@Repository
public interface ImagingInstanceRepository extends JpaRepository<ImagingInstanceEntity, Long> {

    List<ImagingInstanceEntity> findBySeries_IdOrderByInstanceNumberAscIdAsc(Long seriesId);
}
