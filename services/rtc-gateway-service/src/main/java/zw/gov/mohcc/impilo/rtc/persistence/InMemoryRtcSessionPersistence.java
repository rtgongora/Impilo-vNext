package zw.gov.mohcc.impilo.rtc.persistence;

import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory store for unit tests and dev-only fallbacks. */
public class InMemoryRtcSessionPersistence implements RtcSessionPersistence {

    private final ConcurrentMap<String, RtcSessionRecord> sessions = new ConcurrentHashMap<>();

    @Override
    public RtcSessionRecord save(RtcSessionRecord session) {
        sessions.put(session.id(), session);
        return session;
    }

    @Override
    public Optional<RtcSessionRecord> findById(String id) {
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public int countAll() {
        return sessions.size();
    }
}
