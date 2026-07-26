package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicalEpisodeProblemEntity;

import java.util.List;
import java.util.UUID;

public interface MedicalEpisodeProblemRepository
        extends JpaRepository<MedicalEpisodeProblemEntity, MedicalEpisodeProblemEntity.Key> {

    List<MedicalEpisodeProblemEntity> findByIdEpisodeId(UUID episodeId);

    List<MedicalEpisodeProblemEntity> findByTenantIdAndIdProblemId(UUID tenantId, UUID problemId);
}
