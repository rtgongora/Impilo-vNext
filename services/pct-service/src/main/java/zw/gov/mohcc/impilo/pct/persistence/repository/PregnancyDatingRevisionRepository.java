package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyDatingRevisionEntity;

import java.util.List;
import java.util.UUID;

public interface PregnancyDatingRevisionRepository
        extends JpaRepository<PregnancyDatingRevisionEntity, UUID> {

    List<PregnancyDatingRevisionEntity> findByPregnancyEpisodeIdOrderByRevisionNumberDesc(
            UUID pregnancyEpisodeId);

    int countByPregnancyEpisodeId(UUID pregnancyEpisodeId);
}
