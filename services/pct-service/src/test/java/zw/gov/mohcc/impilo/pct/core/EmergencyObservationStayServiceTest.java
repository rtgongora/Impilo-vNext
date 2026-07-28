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

import zw.gov.mohcc.impilo.pct.domain.EmergencyEpisodeState;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyObservationStayEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyObservationStayRepository;

/**
 * Observation / short-stay (pct V208, W9b). The database CHECKs and the partial unique index
 * (one open stay per episode) are already proven on real Postgres; this proves the application layer
 * refuses the same scenarios up front, with a clear message.
 */
public class EmergencyObservationStayServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private EmergencyEpisodeServiceTest.InMemoryRepo episodeRepo;
    private InMemoryStayRepo stayRepo;
    private EmergencyObservationStayService service;
    private UUID episodeId;

    @BeforeEach
    void setUp() {
        episodeRepo = new EmergencyEpisodeServiceTest.InMemoryRepo();
        stayRepo = new InMemoryStayRepo();
        service = new EmergencyObservationStayService(episodeRepo, stayRepo);

        EmergencyEpisodeEntity episode = new EmergencyEpisodeEntity();
        episodeId = UUID.randomUUID();
        episode.setEpisodeId(episodeId);
        episode.setTenantId(TENANT);
        episode.setFacilityId(FACILITY);
        episode.setEpisodeReference("EM-OBS-TEST");
        episode.setEntryRoute("WALK_IN");
        episode.setArrivedAt(OffsetDateTime.now());
        episode.setState(EmergencyEpisodeState.OPEN_IN_CARE.name());
        episodeRepo.save(episode);
    }

    @Test
    @DisplayName("starting a stay against an unknown episode is refused")
    void unknownEpisodeRefused() {
        assertThatThrownBy(() -> service.start(UUID.randomUUID(), TENANT, Map.of("startedBy", "nurse-A")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("starting a stay with no started_by is refused")
    void missingStartedByRefused() {
        assertThatThrownBy(() -> service.start(episodeId, TENANT, Map.of()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a correct start succeeds and is open")
    void startSucceeds() {
        var stay = service.start(episodeId, TENANT, Map.of("startedBy", "nurse-A", "minObservationMinutes", "240"));
        assertThat(stay.getEndedAt()).isNull();
        assertThat(stay.getMinObservationMinutes()).isEqualTo(240);
    }

    @Test
    @DisplayName("a second concurrent open stay for the same episode is refused")
    void secondOpenStayRefused() {
        service.start(episodeId, TENANT, Map.of("startedBy", "nurse-A"));
        assertThatThrownBy(() -> service.start(episodeId, TENANT, Map.of("startedBy", "nurse-B")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("ending a stay with an invalid outcome is refused")
    void invalidOutcomeRefused() {
        var stay = service.start(episodeId, TENANT, Map.of("startedBy", "nurse-A"));
        assertThatThrownBy(() -> service.end(stay.getStayId(), TENANT,
                Map.of("endedBy", "nurse-A", "outcome", "BOGUS")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a correct end succeeds, and a NEW open stay for the same episode is then allowed")
    void endThenReopenSucceeds() {
        var stay = service.start(episodeId, TENANT, Map.of("startedBy", "nurse-A"));
        var ended = service.end(stay.getStayId(), TENANT, Map.of("endedBy", "nurse-A", "outcome", "DISCHARGED"));
        assertThat(ended.getEndedAt()).isNotNull();
        assertThat(ended.getOutcome()).isEqualTo("DISCHARGED");

        var reopened = service.start(episodeId, TENANT, Map.of("startedBy", "nurse-C"));
        assertThat(reopened.getEndedAt()).isNull();
        assertThat(service.forEpisode(episodeId, TENANT)).hasSize(2);
    }

    @Test
    @DisplayName("ending an already-ended stay is refused")
    void doubleEndRefused() {
        var stay = service.start(episodeId, TENANT, Map.of("startedBy", "nurse-A"));
        service.end(stay.getStayId(), TENANT, Map.of("endedBy", "nurse-A", "outcome", "DISCHARGED"));
        assertThatThrownBy(() -> service.end(stay.getStayId(), TENANT, Map.of("endedBy", "nurse-A", "outcome", "DISCHARGED")))
                .isInstanceOf(ResponseStatusException.class);
    }

    /** Minimal in-memory fake — only the methods EmergencyObservationStayService actually calls. */
    public static final class InMemoryStayRepo implements EmergencyObservationStayRepository {
        final Map<UUID, EmergencyObservationStayEntity> store = new LinkedHashMap<>();

        @Override public Optional<EmergencyObservationStayEntity> findByStayIdAndTenantId(UUID stayId, UUID tenantId) {
            var s = store.get(stayId);
            return (s != null && s.getTenantId().equals(tenantId)) ? Optional.of(s) : Optional.empty();
        }
        @Override public Optional<EmergencyObservationStayEntity> findOpen(UUID tenantId, UUID episodeId) {
            return store.values().stream()
                    .filter(s -> s.getTenantId().equals(tenantId) && s.getEpisodeId().equals(episodeId) && s.getEndedAt() == null)
                    .findFirst();
        }
        @Override public List<EmergencyObservationStayEntity> findByTenantIdAndEpisodeIdOrderByStartedAtDesc(UUID tenantId, UUID episodeId) {
            List<EmergencyObservationStayEntity> out = new ArrayList<>();
            for (var s : store.values()) {
                if (s.getTenantId().equals(tenantId) && s.getEpisodeId().equals(episodeId)) out.add(s);
            }
            out.sort((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()));
            return out;
        }
        @Override public <S extends EmergencyObservationStayEntity> S save(S e) {
            boolean isNew = e.getStayId() == null || !store.containsKey(e.getStayId());
            if (isNew && e.getEndedAt() == null && findOpen(e.getTenantId(), e.getEpisodeId()).isPresent()) {
                throw new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_emergency_observation_stay_open\"");
            }
            if (e.getStayId() == null) e.setStayId(UUID.randomUUID());
            if (e.getStartedAt() == null) e.setStartedAt(OffsetDateTime.now());
            store.put(e.getStayId(), e);
            return e;
        }
        @Override public <S extends EmergencyObservationStayEntity> S saveAndFlush(S e) { return save(e); }
        @Override public void flush() { }
        @Override public <S extends EmergencyObservationStayEntity> List<S> saveAllAndFlush(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<EmergencyObservationStayEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public EmergencyObservationStayEntity getOne(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyObservationStayEntity getById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyObservationStayEntity getReferenceById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyObservationStayEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyObservationStayEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyObservationStayEntity> List<S> saveAll(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public List<EmergencyObservationStayEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<EmergencyObservationStayEntity> findAllById(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public Optional<EmergencyObservationStayEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(EmergencyObservationStayEntity e) { store.remove(e.getStayId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends EmergencyObservationStayEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { store.clear(); }
        @Override public List<EmergencyObservationStayEntity> findAll(org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<EmergencyObservationStayEntity> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyObservationStayEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyObservationStayEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyObservationStayEntity> long count(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyObservationStayEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyObservationStayEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }
}
