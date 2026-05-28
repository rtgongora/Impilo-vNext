package zw.gov.mohcc.impilo.rtc;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RtcSessionStore {
    private final ConcurrentMap<String, RtcSessionRecord> sessions = new ConcurrentHashMap<>();

    public RtcSessionRecord save(RtcSessionRecord session) {
        sessions.put(session.id(), session);
        return session;
    }

    public Optional<RtcSessionRecord> get(String id) {
        return Optional.ofNullable(sessions.get(id));
    }
}
