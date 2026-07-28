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
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyMedicationAdminEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyMedicationAdminRepository;

/**
 * ED medication administration (pct V207, W8b) — the application-layer half of the two-person
 * guard. The database CHECKs (chk_ema_*) are already proven on real Postgres (10 probes); this
 * proves the service refuses the same scenarios BEFORE hitting the database, with a clear message.
 */
class EmergencyMedicationAdminServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private EmergencyEpisodeServiceTest.InMemoryRepo episodeRepo;
    private InMemoryAdminRepo adminRepo;
    private EmergencyMedicationAdminService service;
    private UUID episodeId;

    @BeforeEach
    void setUp() {
        episodeRepo = new EmergencyEpisodeServiceTest.InMemoryRepo();
        adminRepo = new InMemoryAdminRepo();
        service = new EmergencyMedicationAdminService(episodeRepo, adminRepo);

        EmergencyEpisodeEntity episode = new EmergencyEpisodeEntity();
        episodeId = UUID.randomUUID();
        episode.setEpisodeId(episodeId);
        episode.setTenantId(TENANT);
        episode.setFacilityId(FACILITY);
        episode.setEpisodeReference("EM-MED-TEST");
        episode.setEntryRoute("WALK_IN");
        episode.setArrivedAt(OffsetDateTime.now());
        episode.setState(EmergencyEpisodeState.OPEN_IN_CARE.name());
        episodeRepo.save(episode);
    }

    private Map<String, Object> routine() {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("drugName", "Paracetamol");
        b.put("doseValue", "1000");
        b.put("doseUnit", "mg");
        b.put("route", "PO");
        b.put("administeredBy", "nurse-A");
        return b;
    }

    private Map<String, Object> highRisk(String administeredBy, String authorisedBy, String witnessedBy) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("drugName", "Morphine");
        b.put("doseValue", "10");
        b.put("doseUnit", "mg");
        b.put("route", "IV");
        b.put("administeredBy", administeredBy);
        b.put("highRisk", true);
        if (authorisedBy != null) b.put("authorisedBy", authorisedBy);
        if (witnessedBy != null) b.put("witnessedBy", witnessedBy);
        return b;
    }

    @Test
    @DisplayName("a routine administration needs no authoriser or witness")
    void routineRequiresNoWitness() {
        var admin = service.record(episodeId, TENANT, routine());
        assertThat(admin.isHighRisk()).isFalse();
        assertThat(admin.getAuthorisedBy()).isNull();
    }

    @Test
    @DisplayName("recording against an unknown episode is refused")
    void unknownEpisodeRefused() {
        assertThatThrownBy(() -> service.record(UUID.randomUUID(), TENANT, routine()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a non-positive dose is refused")
    void nonPositiveDoseRefused() {
        Map<String, Object> b = routine();
        b.put("doseValue", "0");
        assertThatThrownBy(() -> service.record(episodeId, TENANT, b))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("high-risk with neither authoriser nor witness is refused")
    void highRiskRequiresBoth() {
        assertThatThrownBy(() -> service.record(episodeId, TENANT, highRisk("nurse-B", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("required");
    }

    @Test
    @DisplayName("THE INVARIANT: high-risk where authoriser and witness are the same person is refused")
    void authoriserCannotBeWitness() {
        assertThatThrownBy(() -> service.record(episodeId, TENANT, highRisk("nurse-B", "Dr X", "Dr X")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("different people");
    }

    @Test
    @DisplayName("high-risk where the administerer is the sole witness is refused")
    void administererCannotBeSoleWitness() {
        assertThatThrownBy(() -> service.record(episodeId, TENANT, highRisk("nurse-B", "Dr X", "nurse-B")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("sole witness");
    }

    @Test
    @DisplayName("a genuinely correct high-risk administration with three distinct people succeeds")
    void correctHighRiskAdministrationSucceeds() {
        var admin = service.record(episodeId, TENANT, highRisk("nurse-B", "Dr X", "nurse-C"));
        assertThat(admin.isHighRisk()).isTrue();
        assertThat(admin.getAuthorisedBy()).isEqualTo("Dr X");
        assertThat(admin.getWitnessedBy()).isEqualTo("nurse-C");
    }

    @Test
    @DisplayName("a duplicate client_event_id maps to 409, not an opaque failure")
    void duplicateClientEventIdMapsToConflict() {
        adminRepo.throwDuplicateOnNextSave = true;
        assertThatThrownBy(() -> service.record(episodeId, TENANT, routine()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already recorded");
    }

    @Test
    @DisplayName("forEpisode returns administrations for that episode, most recent first")
    void forEpisodeReturnsAdmins() {
        service.record(episodeId, TENANT, routine());
        service.record(episodeId, TENANT, highRisk("nurse-B", "Dr X", "nurse-C"));
        assertThat(service.forEpisode(episodeId, TENANT)).hasSize(2);
    }

    /** Minimal in-memory fake — only the methods EmergencyMedicationAdminService actually calls. */
    private static final class InMemoryAdminRepo implements EmergencyMedicationAdminRepository {
        final Map<UUID, EmergencyMedicationAdminEntity> store = new LinkedHashMap<>();
        boolean throwDuplicateOnNextSave = false;

        @Override public List<EmergencyMedicationAdminEntity> findByTenantIdAndEpisodeIdOrderByAdministeredAtDesc(UUID tenant, UUID episodeId) {
            List<EmergencyMedicationAdminEntity> out = new ArrayList<>();
            for (var e : store.values()) {
                if (e.getTenantId().equals(tenant) && e.getEpisodeId().equals(episodeId)) out.add(e);
            }
            out.sort((a, b) -> b.getAdministeredAt().compareTo(a.getAdministeredAt()));
            return out;
        }
        @Override public <S extends EmergencyMedicationAdminEntity> S save(S e) {
            if (throwDuplicateOnNextSave) {
                throw new org.springframework.dao.DataIntegrityViolationException("duplicate client_event_id");
            }
            if (e.getAdminId() == null) e.setAdminId(UUID.randomUUID());
            if (e.getAdministeredAt() == null) e.setAdministeredAt(OffsetDateTime.now());
            store.put(e.getAdminId(), e);
            return e;
        }
        @Override public <S extends EmergencyMedicationAdminEntity> S saveAndFlush(S e) { return save(e); }
        @Override public void flush() { }
        @Override public <S extends EmergencyMedicationAdminEntity> List<S> saveAllAndFlush(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<EmergencyMedicationAdminEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public EmergencyMedicationAdminEntity getOne(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyMedicationAdminEntity getById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyMedicationAdminEntity getReferenceById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyMedicationAdminEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyMedicationAdminEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyMedicationAdminEntity> List<S> saveAll(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public List<EmergencyMedicationAdminEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<EmergencyMedicationAdminEntity> findAllById(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public Optional<EmergencyMedicationAdminEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(EmergencyMedicationAdminEntity e) { store.remove(e.getAdminId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends EmergencyMedicationAdminEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { store.clear(); }
        @Override public List<EmergencyMedicationAdminEntity> findAll(org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<EmergencyMedicationAdminEntity> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyMedicationAdminEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyMedicationAdminEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyMedicationAdminEntity> long count(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyMedicationAdminEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyMedicationAdminEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }
}
