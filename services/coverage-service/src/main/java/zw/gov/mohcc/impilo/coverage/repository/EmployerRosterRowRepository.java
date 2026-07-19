package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.EmployerRosterRowEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmployerRosterRowRepository extends JpaRepository<EmployerRosterRowEntity, UUID> {
    List<EmployerRosterRowEntity> findByBatchId(UUID batchId);
}
