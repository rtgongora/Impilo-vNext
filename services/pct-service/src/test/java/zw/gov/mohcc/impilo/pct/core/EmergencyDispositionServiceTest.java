package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;

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
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyDispositionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyDispositionRepository;

/**
 * The episode-level disposition record (pct V208, W9b) — R12's third mandatory-content gate. The
 * database CHECKs (chk_ed_disp_*) are already proven on real Postgres (18 probes); this proves the
 * service refuses the same scenarios BEFORE hitting the database, AND that the right subset of
 * disposition types drives the episode FSM to a closed state while the rest (admissions/transfers,
 * OBSERVATION) deliberately do not.
 */
public class EmergencyDispositionServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private EmergencyEpisodeServiceTest.InMemoryRepo episodeRepo;
    private InMemoryDispositionRepo dispositionRepo;
    private EmergencyEpisodeService episodeService;
    private EmergencyDispositionService service;

    @BeforeEach
    void setUp() {
        episodeRepo = new EmergencyEpisodeServiceTest.InMemoryRepo();
        dispositionRepo = new InMemoryDispositionRepo();
        var handoverRepo = new EmergencyEpisodeServiceTest.InMemoryHandoverRepo();
        var outbox = new EmergencyEpisodeServiceTest.CountingOutbox();
        episodeService = new EmergencyEpisodeService(episodeRepo, handoverRepo, outbox, new ObjectMapper());
        service = new EmergencyDispositionService(episodeRepo, dispositionRepo, episodeService);
    }

    private UUID seedEpisode(EmergencyEpisodeState state) {
        EmergencyEpisodeEntity episode = new EmergencyEpisodeEntity();
        UUID episodeId = UUID.randomUUID();
        episode.setEpisodeId(episodeId);
        episode.setTenantId(TENANT);
        episode.setFacilityId(FACILITY);
        episode.setEpisodeReference("EM-DISP-" + episodeId);
        episode.setEntryRoute("WALK_IN");
        episode.setArrivedAt(OffsetDateTime.now());
        episode.setState(state.name());
        episodeRepo.save(episode);
        return episodeId;
    }

    private Map<String, Object> disposition(String type) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("dispositionType", type);
        b.put("dispositionReason", "clinically appropriate");
        b.put("disposedBy", "dr-x");
        return b;
    }

    @Test
    @DisplayName("recording against an unknown episode is refused")
    void unknownEpisodeRefused() {
        assertThatThrownBy(() -> service.record(UUID.randomUUID(), TENANT, disposition("DISCHARGED_HOME")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("an unknown disposition_type is refused")
    void unknownTypeRefused() {
        UUID episodeId = seedEpisode(EmergencyEpisodeState.OPEN_IN_CARE);
        assertThatThrownBy(() -> service.record(episodeId, TENANT, disposition("BOGUS")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("mandatory content: missing reason is refused")
    void missingReasonRefused() {
        UUID episodeId = seedEpisode(EmergencyEpisodeState.OPEN_IN_CARE);
        Map<String, Object> b = disposition("DISCHARGED_HOME");
        b.remove("dispositionReason");
        assertThatThrownBy(() -> service.record(episodeId, TENANT, b))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("who, when and why");
    }

    @Test
    @DisplayName("an admission with no destination is refused")
    void destinationRequiredRefused() {
        UUID episodeId = seedEpisode(EmergencyEpisodeState.OPEN_IN_CARE);
        assertThatThrownBy(() -> service.record(episodeId, TENANT, disposition("ADMITTED_WARD")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("destination");
    }

    @Test
    @DisplayName("DIED with no cause/circumstances is refused")
    void deathCircumstancesRequiredRefused() {
        UUID episodeId = seedEpisode(EmergencyEpisodeState.OPEN_IN_CARE);
        assertThatThrownBy(() -> service.record(episodeId, TENANT, disposition("DIED")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cause_or_circumstances");
    }

    @Test
    @DisplayName("ABSCONDED with no last_seen_at is refused")
    void lastSeenRequiredRefused() {
        UUID episodeId = seedEpisode(EmergencyEpisodeState.OPEN_IN_CARE);
        assertThatThrownBy(() -> service.record(episodeId, TENANT, disposition("ABSCONDED")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("last_seen_at");
    }

    @Test
    @DisplayName("a correct DISCHARGED_HOME succeeds AND transitions the episode to CLOSED_DISCHARGED")
    void dischargedHomeClosesEpisode() {
        UUID episodeId = seedEpisode(EmergencyEpisodeState.OPEN_IN_CARE);
        EmergencyDispositionEntity d = service.record(episodeId, TENANT, disposition("DISCHARGED_HOME"));
        assertThat(d.getDispositionType()).isEqualTo("DISCHARGED_HOME");
        assertThat(episodeRepo.findByEpisodeIdAndTenantId(episodeId, TENANT).orElseThrow().getState())
                .isEqualTo("CLOSED_DISCHARGED");
    }

    @Test
    @DisplayName("a correct DIED succeeds AND transitions the episode to CLOSED_DIED")
    void diedClosesEpisode() {
        UUID episodeId = seedEpisode(EmergencyEpisodeState.OPEN_IN_CARE);
        Map<String, Object> b = disposition("DIED");
        b.put("causeOrCircumstances", "refractory VF arrest");
        service.record(episodeId, TENANT, b);
        assertThat(episodeRepo.findByEpisodeIdAndTenantId(episodeId, TENANT).orElseThrow().getState())
                .isEqualTo("CLOSED_DIED");
    }

    @Test
    @DisplayName("THE ACCEPTANCE-HANDSHAKE BOUNDARY: a correct ADMITTED_WARD succeeds but does NOT close the episode")
    void admittedWardDoesNotCloseEpisode() {
        UUID episodeId = seedEpisode(EmergencyEpisodeState.OPEN_IN_CARE);
        Map<String, Object> b = disposition("ADMITTED_WARD");
        b.put("destination", "Ward 4B");
        service.record(episodeId, TENANT, b);
        // Still OPEN_IN_CARE — only an accepted handover (V204) may close to CLOSED_HANDED_OVER.
        assertThat(episodeRepo.findByEpisodeIdAndTenantId(episodeId, TENANT).orElseThrow().getState())
                .isEqualTo("OPEN_IN_CARE");
    }

    @Test
    @DisplayName("OBSERVATION succeeds and leaves the episode OPEN_IN_CARE")
    void observationDoesNotCloseEpisode() {
        UUID episodeId = seedEpisode(EmergencyEpisodeState.OPEN_IN_CARE);
        service.record(episodeId, TENANT, disposition("OBSERVATION"));
        assertThat(episodeRepo.findByEpisodeIdAndTenantId(episodeId, TENANT).orElseThrow().getState())
                .isEqualTo("OPEN_IN_CARE");
    }

    @Test
    @DisplayName("a disposition that contradicts the episode's actual FSM state is refused, not silently applied")
    void fsmMismatchRefused() {
        // LEFT_BEFORE_ASSESSMENT is only a valid transition from OPEN_UNTRIAGED. In the real service
        // this row never survives — @Transactional rolls back the disposition save alongside the
        // failed transition; the in-memory fake here has no transaction manager to simulate that, so
        // only the thrown-exception contract is asserted (rollback correctness is proven separately,
        // on real Postgres).
        UUID episodeId = seedEpisode(EmergencyEpisodeState.OPEN_IN_CARE);
        assertThatThrownBy(() -> service.record(episodeId, TENANT, disposition("LEFT_BEFORE_ASSESSMENT")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a second disposition for the same episode is refused (one per episode)")
    void secondDispositionRefused() {
        UUID episodeId = seedEpisode(EmergencyEpisodeState.OPEN_IN_CARE);
        service.record(episodeId, TENANT, disposition("DISCHARGED_HOME"));
        assertThatThrownBy(() -> service.record(episodeId, TENANT, disposition("DISCHARGED_HOME")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("forEpisode returns the recorded disposition")
    void forEpisodeReturnsDisposition() {
        UUID episodeId = seedEpisode(EmergencyEpisodeState.OPEN_IN_CARE);
        service.record(episodeId, TENANT, disposition("DISCHARGED_HOME"));
        assertThat(service.forEpisode(episodeId, TENANT).getDispositionType()).isEqualTo("DISCHARGED_HOME");
    }

    /** Minimal in-memory fake — only the methods EmergencyDispositionService actually calls. */
    public static final class InMemoryDispositionRepo implements EmergencyDispositionRepository {
        final Map<UUID, EmergencyDispositionEntity> store = new LinkedHashMap<>();

        @Override public Optional<EmergencyDispositionEntity> findByEpisodeIdAndTenantId(UUID episodeId, UUID tenantId) {
            return store.values().stream()
                    .filter(d -> d.getEpisodeId().equals(episodeId) && d.getTenantId().equals(tenantId))
                    .findFirst();
        }
        @Override public boolean existsByEpisodeIdAndTenantId(UUID episodeId, UUID tenantId) {
            return findByEpisodeIdAndTenantId(episodeId, tenantId).isPresent();
        }
        @Override public <S extends EmergencyDispositionEntity> S save(S e) {
            boolean isNew = e.getDispositionId() == null || !store.containsKey(e.getDispositionId());
            if (isNew && existsByEpisodeIdAndTenantId(e.getEpisodeId(), e.getTenantId())) {
                throw new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"emergency_disposition_episode_id_key\"");
            }
            if (e.getDispositionId() == null) e.setDispositionId(UUID.randomUUID());
            if (e.getDisposedAt() == null) e.setDisposedAt(OffsetDateTime.now());
            store.put(e.getDispositionId(), e);
            return e;
        }
        @Override public <S extends EmergencyDispositionEntity> S saveAndFlush(S e) { return save(e); }
        @Override public void flush() { }
        @Override public <S extends EmergencyDispositionEntity> List<S> saveAllAndFlush(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<EmergencyDispositionEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public EmergencyDispositionEntity getOne(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyDispositionEntity getById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyDispositionEntity getReferenceById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyDispositionEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyDispositionEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyDispositionEntity> List<S> saveAll(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public List<EmergencyDispositionEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<EmergencyDispositionEntity> findAllById(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public Optional<EmergencyDispositionEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(EmergencyDispositionEntity e) { store.remove(e.getDispositionId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends EmergencyDispositionEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { store.clear(); }
        @Override public List<EmergencyDispositionEntity> findAll(org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<EmergencyDispositionEntity> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyDispositionEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyDispositionEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyDispositionEntity> long count(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyDispositionEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyDispositionEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }
}
