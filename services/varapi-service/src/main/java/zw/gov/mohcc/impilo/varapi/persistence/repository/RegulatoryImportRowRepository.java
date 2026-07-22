package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.RegulatoryImportRowEntity;

import java.util.List;

@Repository
public interface RegulatoryImportRowRepository extends JpaRepository<RegulatoryImportRowEntity, Long> {
    List<RegulatoryImportRowEntity> findByBatchIdOrderByRowIndexAsc(Long batchId);
}
