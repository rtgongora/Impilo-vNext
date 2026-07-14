package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureAnaesthesiaChartEntryEntity;

import java.util.List;
import java.util.UUID;

public interface ProcedureAnaesthesiaChartEntryRepository
        extends JpaRepository<ProcedureAnaesthesiaChartEntryEntity, UUID> {
    List<ProcedureAnaesthesiaChartEntryEntity> findByEpisodeIdOrderByRecordedAtAscChannelAsc(UUID episodeId);
    int countByEpisodeIdAndChannel(UUID episodeId, String channel);
}
