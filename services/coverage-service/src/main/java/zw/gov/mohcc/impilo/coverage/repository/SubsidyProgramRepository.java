package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyProgramEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubsidyProgramRepository extends JpaRepository<SubsidyProgramEntity, UUID> {

    List<SubsidyProgramEntity> findByTenantIdAndStatus(UUID tenantId, String status);
}
