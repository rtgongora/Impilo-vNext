package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgrammeSourceReferenceRepository extends JpaRepository<ProgrammeSourceReferenceEntity, UUID> {
    Optional<ProgrammeSourceReferenceEntity> findBySourceServiceAndSourceValue(String sourceService, String sourceValue);
    List<ProgrammeSourceReferenceEntity> findByProgrammeId(UUID programmeId);
}
