package zw.gov.mohcc.impilo.surgery.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurgicalEpisodeRepository extends JpaRepository<SurgicalEpisodeEntity, UUID> {

    Optional<SurgicalEpisodeEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SurgicalEpisodeEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
