package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProgrammeFacilityRepository extends JpaRepository<ProgrammeFacilityEntity, UUID> {
    List<ProgrammeFacilityEntity> findByProgrammeIdAndStatus(UUID programmeId, String status);
    List<ProgrammeFacilityEntity> findByFacilityIdAndStatus(String facilityId, String status);
}
