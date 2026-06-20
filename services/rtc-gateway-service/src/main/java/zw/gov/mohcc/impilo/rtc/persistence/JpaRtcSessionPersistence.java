package zw.gov.mohcc.impilo.rtc.persistence;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;
import zw.gov.mohcc.impilo.rtc.persistence.entity.RtcSessionEntity;
import zw.gov.mohcc.impilo.rtc.persistence.repository.RtcSessionEntityRepository;

import java.util.Optional;

@Component
@Primary
public class JpaRtcSessionPersistence implements RtcSessionPersistence {

    private final RtcSessionEntityRepository repository;

    public JpaRtcSessionPersistence(RtcSessionEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    public RtcSessionRecord save(RtcSessionRecord session) {
        return repository.save(RtcSessionEntity.fromRecord(session)).toRecord();
    }

    @Override
    public Optional<RtcSessionRecord> findById(String id) {
        return repository.findById(id).map(RtcSessionEntity::toRecord);
    }

    @Override
    public int countAll() {
        return (int) repository.count();
    }
}
