package zw.gov.mohcc.impilo.surgery.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeSpecialtyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurgicalEpisodeSpecialtyRepository extends JpaRepository<SurgicalEpisodeSpecialtyEntity, UUID> {

    List<SurgicalEpisodeSpecialtyEntity> findByEpisodeIdOrderByRoleAscSpecialtyAsc(UUID episodeId);

    Optional<SurgicalEpisodeSpecialtyEntity> findByEpisodeIdAndRole(UUID episodeId, String role);

    Optional<SurgicalEpisodeSpecialtyEntity> findByEpisodeIdAndSpecialty(UUID episodeId, String specialty);
}
