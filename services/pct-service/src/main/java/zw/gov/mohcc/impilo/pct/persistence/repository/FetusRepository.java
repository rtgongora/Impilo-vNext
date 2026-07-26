package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.FetusEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FetusRepository extends JpaRepository<FetusEntity, UUID> {

    List<FetusEntity> findByPregnancyEpisodeIdOrderByFetusLabelAsc(UUID pregnancyEpisodeId);

    Optional<FetusEntity> findByPregnancyEpisodeIdAndFetusLabel(UUID pregnancyEpisodeId, String fetusLabel);

    Optional<FetusEntity> findByNewbornBirthRecordId(UUID newbornBirthRecordId);
}
