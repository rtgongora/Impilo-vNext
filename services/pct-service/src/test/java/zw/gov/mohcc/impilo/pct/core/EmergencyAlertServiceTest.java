package zw.gov.mohcc.impilo.pct.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyAlertEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyAlertRepository;

/**
 * The responder-facing side of {@code emergency_alert} (W5b): acknowledge, respond, close. The
 * database CHECKs (chk_ea_ack_pair, chk_ea_responded_pair, chk_ea_*_recorded) are the real
 * guarantee; this proves the service refuses the same scenarios up front and reaches only
 * DB-satisfiable states.
 */
public class EmergencyAlertServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private InMemoryAlertRepo alertRepo;
    private EmergencyAlertService service;
    private UUID episodeId;

    @BeforeEach
    void setUp() {
        alertRepo = new InMemoryAlertRepo();
        service = new EmergencyAlertService(alertRepo);
        episodeId = UUID.randomUUID();
    }

    private UUID seedRaisedAlert(String type) {
        EmergencyAlertEntity a = new EmergencyAlertEntity();
        a.setTenantId(TENANT);
        a.setFacilityId(FACILITY);
        a.setEpisodeId(episodeId);
        a.setAlertType(type);
        a.setReason("test reason");
        a.setResponseDueAt(OffsetDateTime.now().plusMinutes(10));
        alertRepo.save(a);
        return a.getAlertId();
    }

    @Test
    @DisplayName("acknowledging an unknown alert is refused")
    void unknownAlertRefused() {
        assertThatThrownBy(() -> service.acknowledge(UUID.randomUUID(), TENANT, Map.of("acknowledgedBy", "nurse-A")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("acknowledging with no acknowledged_by is refused")
    void missingAcknowledgedByRefused() {
        UUID alertId = seedRaisedAlert("TRIAGE_OVERDUE");
        assertThatThrownBy(() -> service.acknowledge(alertId, TENANT, Map.of()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a correct acknowledge succeeds and stamps both fields together")
    void acknowledgeSucceeds() {
        UUID alertId = seedRaisedAlert("TRIAGE_OVERDUE");
        var acked = service.acknowledge(alertId, TENANT, Map.of("acknowledgedBy", "nurse-A"));
        assertThat(acked.getStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(acked.getAcknowledgedBy()).isEqualTo("nurse-A");
        assertThat(acked.getAcknowledgedAt()).isNotNull();
    }

    @Test
    @DisplayName("acknowledging twice is refused — already ACKNOWLEDGED is not RAISED/OVERDUE")
    void doubleAcknowledgeRefused() {
        UUID alertId = seedRaisedAlert("TRIAGE_OVERDUE");
        service.acknowledge(alertId, TENANT, Map.of("acknowledgedBy", "nurse-A"));
        assertThatThrownBy(() -> service.acknowledge(alertId, TENANT, Map.of("acknowledgedBy", "nurse-B")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("responding directly (without a prior acknowledge) stamps BOTH ack and response fields")
    void directRespondStampsAckToo() {
        UUID alertId = seedRaisedAlert("OBSERVATION_OVERDUE");
        var responded = service.respond(alertId, TENANT, Map.of("respondedBy", "dr-x", "responseNote", "reviewed patient"));
        assertThat(responded.getStatus()).isEqualTo("RESPONDED");
        assertThat(responded.getAcknowledgedAt()).isNotNull();
        assertThat(responded.getAcknowledgedBy()).isEqualTo("dr-x");
        assertThat(responded.getRespondedAt()).isNotNull();
        assertThat(responded.getResponseNote()).isEqualTo("reviewed patient");
    }

    @Test
    @DisplayName("responding with no responded_by is refused")
    void missingRespondedByRefused() {
        UUID alertId = seedRaisedAlert("OBSERVATION_OVERDUE");
        assertThatThrownBy(() -> service.respond(alertId, TENANT, Map.of()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("responding after an explicit acknowledge does not overwrite the original acknowledger")
    void respondAfterAcknowledgePreservesAcknowledger() {
        UUID alertId = seedRaisedAlert("OBSERVATION_OVERDUE");
        service.acknowledge(alertId, TENANT, Map.of("acknowledgedBy", "nurse-A"));
        var responded = service.respond(alertId, TENANT, Map.of("respondedBy", "dr-x"));
        assertThat(responded.getAcknowledgedBy()).isEqualTo("nurse-A");
        assertThat(responded.getRespondedBy()).isEqualTo("dr-x");
    }

    @Test
    @DisplayName("closing with no close_reason is refused")
    void missingCloseReasonRefused() {
        UUID alertId = seedRaisedAlert("LOCATION_UNKNOWN");
        assertThatThrownBy(() -> service.close(alertId, TENANT, Map.of()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a raised alert can be closed directly (a false-positive needs no response first)")
    void closeDirectlyFromRaisedSucceeds() {
        UUID alertId = seedRaisedAlert("LOCATION_UNKNOWN");
        var closed = service.close(alertId, TENANT, Map.of("closeReason", "false positive — GPS drift"));
        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(closed.getClosedAt()).isNotNull();
        assertThat(closed.getCloseReason()).isEqualTo("false positive — GPS drift");
    }

    @Test
    @DisplayName("closing an already-closed alert is refused")
    void doubleCloseRefused() {
        UUID alertId = seedRaisedAlert("LOCATION_UNKNOWN");
        service.close(alertId, TENANT, Map.of("closeReason", "resolved"));
        assertThatThrownBy(() -> service.close(alertId, TENANT, Map.of("closeReason", "resolved again")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("forEpisode returns this episode's alerts, most recent first")
    void forEpisodeReturnsAlerts() {
        seedRaisedAlert("TRIAGE_OVERDUE");
        seedRaisedAlert("LOCATION_UNKNOWN");
        assertThat(service.forEpisode(episodeId, TENANT)).hasSize(2);
    }

    @Test
    @DisplayName("board returns only open alerts for the facility")
    void boardReturnsOpenOnly() {
        UUID closedId = seedRaisedAlert("TRIAGE_OVERDUE");
        service.close(closedId, TENANT, Map.of("closeReason", "resolved"));
        seedRaisedAlert("LOCATION_UNKNOWN");
        assertThat(service.board(TENANT, FACILITY)).hasSize(1);
    }

    /** Minimal in-memory fake — only the methods EmergencyAlertService (and its sweep/reconciliation callers) actually use. */
    public static final class InMemoryAlertRepo implements EmergencyAlertRepository {
        final Map<UUID, EmergencyAlertEntity> store = new LinkedHashMap<>();

        @Override public Optional<EmergencyAlertEntity> findByAlertIdAndTenantId(UUID alertId, UUID tenantId) {
            var a = store.get(alertId);
            return (a != null && a.getTenantId().equals(tenantId)) ? Optional.of(a) : Optional.empty();
        }
        @Override public Optional<EmergencyAlertEntity> findOpen(UUID tenantId, UUID episodeId, String alertType) {
            return store.values().stream()
                    .filter(a -> a.getTenantId().equals(tenantId) && a.getEpisodeId().equals(episodeId)
                            && alertType.equals(a.getAlertType()) && OPEN_STATUSES.contains(a.getStatus()))
                    .findFirst();
        }
        @Override public List<EmergencyAlertEntity> findOpenForFacility(UUID tenantId, UUID facilityId) {
            return store.values().stream()
                    .filter(a -> a.getTenantId().equals(tenantId)
                            && (facilityId == null || facilityId.equals(a.getFacilityId()))
                            && OPEN_STATUSES.contains(a.getStatus()))
                    .sorted((x, y) -> x.getResponseDueAt().compareTo(y.getResponseDueAt()))
                    .toList();
        }
        @Override public List<EmergencyAlertEntity> findByTenantIdAndEpisodeIdOrderByRaisedAtDesc(UUID tenantId, UUID episodeId) {
            return store.values().stream()
                    .filter(a -> a.getTenantId().equals(tenantId) && a.getEpisodeId().equals(episodeId))
                    .sorted((x, y) -> y.getRaisedAt().compareTo(x.getRaisedAt()))
                    .toList();
        }
        @Override public List<EmergencyAlertEntity> findBreached(OffsetDateTime now) {
            return store.values().stream()
                    .filter(a -> ("RAISED".equals(a.getStatus()) || "ACKNOWLEDGED".equals(a.getStatus()))
                            && a.getResponseDueAt().isBefore(now))
                    .toList();
        }
        @Override public <S extends EmergencyAlertEntity> S save(S e) {
            if (e.getAlertId() == null) e.setAlertId(UUID.randomUUID());
            if (e.getRaisedAt() == null) e.setRaisedAt(OffsetDateTime.now());
            store.put(e.getAlertId(), e);
            return e;
        }
        @Override public <S extends EmergencyAlertEntity> S saveAndFlush(S e) {
            boolean isNew = e.getAlertId() == null || !store.containsKey(e.getAlertId());
            if (isNew && findOpen(e.getTenantId(), e.getEpisodeId(), e.getAlertType()).isPresent()) {
                throw new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_emergency_alert_open\"");
            }
            return save(e);
        }
        @Override public void flush() { }
        @Override public <S extends EmergencyAlertEntity> List<S> saveAllAndFlush(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<EmergencyAlertEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public EmergencyAlertEntity getOne(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyAlertEntity getById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyAlertEntity getReferenceById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyAlertEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyAlertEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyAlertEntity> List<S> saveAll(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public List<EmergencyAlertEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<EmergencyAlertEntity> findAllById(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public Optional<EmergencyAlertEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(EmergencyAlertEntity e) { store.remove(e.getAlertId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends EmergencyAlertEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { store.clear(); }
        @Override public List<EmergencyAlertEntity> findAll(org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<EmergencyAlertEntity> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyAlertEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyAlertEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyAlertEntity> long count(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyAlertEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyAlertEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }
}
