package zw.gov.mohcc.impilo.rtc.persistence;

import zw.gov.mohcc.impilo.rtc.model.RtcParticipantRecord;

import java.util.List;
import java.util.Optional;

/**
 * Lobby participant rows (rtc.session_participants), keyed on (sessionId, identity).
 *
 * <p>Upsert semantics (identical across implementations): the incoming record's
 * state/admittedAt/admittedBy/deniedReason are authoritative; displayName, role
 * and mediaProfile only overwrite when non-null; requestedAt of an existing row
 * is always preserved.
 */
public interface RtcParticipantPersistence {

    RtcParticipantRecord upsert(RtcParticipantRecord participant);

    Optional<RtcParticipantRecord> find(String sessionId, String identity);

    List<RtcParticipantRecord> findBySession(String sessionId);

    /** State-only transition (webhook joined/left); no-op when the row does not exist. */
    void updateState(String sessionId, String identity, String state);
}
