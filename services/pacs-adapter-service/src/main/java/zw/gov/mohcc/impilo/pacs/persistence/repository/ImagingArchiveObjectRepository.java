package zw.gov.mohcc.impilo.pacs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingArchiveObjectEntity;

@Repository
public interface ImagingArchiveObjectRepository extends JpaRepository<ImagingArchiveObjectEntity, Long> {
}
