package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProgrammeServiceRepository extends JpaRepository<ProgrammeServiceEntity, UUID> {
    List<ProgrammeServiceEntity> findByProgrammeId(UUID programmeId);
}
