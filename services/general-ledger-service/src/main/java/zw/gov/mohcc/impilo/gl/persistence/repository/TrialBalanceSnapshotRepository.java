package zw.gov.mohcc.impilo.gl.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.gl.persistence.entity.TrialBalanceSnapshotEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrialBalanceSnapshotRepository extends JpaRepository<TrialBalanceSnapshotEntity, UUID> {

    List<TrialBalanceSnapshotEntity> findByTenantIdAndFiscalPeriodIdOrderByGeneratedAtDesc(
            UUID tenantId, UUID fiscalPeriodId);
}
