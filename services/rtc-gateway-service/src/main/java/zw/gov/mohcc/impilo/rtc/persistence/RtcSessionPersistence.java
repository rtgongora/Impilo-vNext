package zw.gov.mohcc.impilo.rtc.persistence;

import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;

import java.util.Optional;

public interface RtcSessionPersistence {

    RtcSessionRecord save(RtcSessionRecord session);

    Optional<RtcSessionRecord> findById(String id);

    int countAll();
}
