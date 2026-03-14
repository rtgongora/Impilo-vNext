package zw.gov.mohcc.impilo.datawarehouse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.datawarehouse.domain.MaterializerWatermarkEntity;

import java.util.Optional;

public interface MaterializerWatermarkRepository extends JpaRepository<MaterializerWatermarkEntity, Long> {

    Optional<MaterializerWatermarkEntity> findBySourceTable(String sourceTable);
}
