package zw.gov.mohcc.impilo.rtc.persistence;

import zw.gov.mohcc.impilo.rtc.model.RtcParticipantRecord;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory lobby store for unit tests; mirrors the JDBC upsert semantics. */
public class InMemoryRtcParticipantPersistence implements RtcParticipantPersistence {

    private final ConcurrentMap<String, RtcParticipantRecord> participants = new ConcurrentHashMap<>();

    @Override
    public RtcParticipantRecord upsert(RtcParticipantRecord p) {
        return participants.compute(key(p.sessionId(), p.identity()), (k, existing) -> {
            if (existing == null) {
                return p.requestedAt() == null
                        ? withRequestedAt(p, Instant.now())
                        : p;
            }
            return new RtcParticipantRecord(
                    p.sessionId(), p.identity(),
                    p.displayName() != null ? p.displayName() : existing.displayName(),
                    p.role() != null ? p.role() : existing.role(),
                    p.state(),
                    p.mediaProfile() != null ? p.mediaProfile() : existing.mediaProfile(),
                    existing.requestedAt(),
                    p.admittedAt(), p.admittedBy(), p.deniedReason());
        });
    }

    @Override
    public Optional<RtcParticipantRecord> find(String sessionId, String identity) {
        return Optional.ofNullable(participants.get(key(sessionId, identity)));
    }

    @Override
    public List<RtcParticipantRecord> findBySession(String sessionId) {
        return participants.values().stream()
                .filter(p -> p.sessionId().equals(sessionId))
                .sorted(Comparator.comparing(RtcParticipantRecord::requestedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    public void updateState(String sessionId, String identity, String state) {
        participants.computeIfPresent(key(sessionId, identity), (k, existing) ->
                new RtcParticipantRecord(
                        existing.sessionId(), existing.identity(), existing.displayName(), existing.role(),
                        state, existing.mediaProfile(), existing.requestedAt(),
                        existing.admittedAt(), existing.admittedBy(), existing.deniedReason()));
    }

    private static RtcParticipantRecord withRequestedAt(RtcParticipantRecord p, Instant requestedAt) {
        return new RtcParticipantRecord(
                p.sessionId(), p.identity(), p.displayName(), p.role(), p.state(),
                p.mediaProfile(), requestedAt, p.admittedAt(), p.admittedBy(), p.deniedReason());
    }

    private static String key(String sessionId, String identity) {
        return sessionId + "|" + identity;
    }
}
