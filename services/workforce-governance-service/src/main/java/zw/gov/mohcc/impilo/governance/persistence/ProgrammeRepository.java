package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgrammeRepository extends JpaRepository<ProgrammeEntity, UUID> {

    Optional<ProgrammeEntity> findByTenantIdAndProgrammeCode(UUID tenantId, String programmeCode);

    List<ProgrammeEntity> findByTenantIdOrderByNameAsc(UUID tenantId);

    List<ProgrammeEntity> findByTenantIdAndStatusOrderByNameAsc(UUID tenantId, String status);

    List<ProgrammeEntity> findByParentProgrammeIdOrderByNameAsc(UUID parentProgrammeId);

    /** Used by AssignmentService to validate a PROGRAM-target assignment's target_id. */
    Optional<ProgrammeEntity> findByTenantIdAndIdAndStatus(UUID tenantId, UUID id, String status);
}
