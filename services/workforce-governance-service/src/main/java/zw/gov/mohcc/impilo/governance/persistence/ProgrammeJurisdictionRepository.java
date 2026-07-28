package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProgrammeJurisdictionRepository extends JpaRepository<ProgrammeJurisdictionEntity, UUID> {
    List<ProgrammeJurisdictionEntity> findByProgrammeIdAndStatus(UUID programmeId, String status);
    List<ProgrammeJurisdictionEntity> findByJurisdictionIdAndStatus(UUID jurisdictionId, String status);
}
