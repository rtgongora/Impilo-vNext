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

import com.fasterxml.jackson.databind.ObjectMapper;

import zw.gov.mohcc.impilo.pct.domain.EmergencyEpisodeState;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyIdentityLinkEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EmergencyIdentityLinkRepository;

/**
 * The clinical identity-resolution audit trail (pct V210, W12). The database's own invariants — at
 * most one active link per episode, the closed resolution-method vocabulary — are proven on real
 * Postgres separately; this proves the application layer enforces the same rules up front and that
 * a correction supersedes rather than overwrites.
 */
public class EmergencyIdentityLinkServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private EmergencyEpisodeServiceTest.InMemoryRepo episodeRepo;
    private InMemoryIdentityLinkRepo linkRepo;
    private EmergencyEpisodeServiceTest.CountingOutbox outbox;
    private EmergencyIdentityLinkService service;
    private UUID episodeId;

    @BeforeEach
    void setUp() {
        episodeRepo = new EmergencyEpisodeServiceTest.InMemoryRepo();
        linkRepo = new InMemoryIdentityLinkRepo();
        outbox = new EmergencyEpisodeServiceTest.CountingOutbox();
        service = new EmergencyIdentityLinkService(episodeRepo, linkRepo, outbox, new ObjectMapper());

        EmergencyEpisodeEntity episode = new EmergencyEpisodeEntity();
        episodeId = UUID.randomUUID();
        episode.setEpisodeId(episodeId);
        episode.setTenantId(TENANT);
        episode.setFacilityId(FACILITY);
        episode.setEpisodeReference("EM-EIL-TEST");
        episode.setEntryRoute("UNKNOWN_OR_UNRESPONSIVE");
        episode.setArrivedAt(OffsetDateTime.now());
        episode.setState(EmergencyEpisodeState.OPEN_IN_CARE.name());
        episode.setIdentityMode("UNKNOWN");
        episodeRepo.save(episode);
    }

    @Test
    @DisplayName("linking identity on an unknown episode is refused")
    void unknownEpisodeRefused() {
        assertThatThrownBy(() -> service.linkIdentity(TENANT, UUID.randomUUID(), "CPID-1",
                "BIOMETRIC", null, null, "nurse-A"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a blank resolved cpid is refused")
    void blankCpidRefused() {
        assertThatThrownBy(() -> service.linkIdentity(TENANT, episodeId, "  ",
                "BIOMETRIC", null, null, "nurse-A"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a resolution method outside the closed vocabulary is refused")
    void badResolutionMethodRefused() {
        assertThatThrownBy(() -> service.linkIdentity(TENANT, episodeId, "CPID-1",
                "VITO_MERGE", null, null, "nurse-A"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a correct link succeeds, records the previous UNKNOWN identity, and updates the episode")
    void linkSucceedsAndUpdatesEpisode() {
        var link = service.linkIdentity(TENANT, episodeId, "CPID-1", "SMART_CARD", "ref-1", null, "nurse-A");

        assertThat(link.getPreviousIdentityMode()).isEqualTo("UNKNOWN");
        assertThat(link.getPreviousSubjectCpid()).isNull();
        assertThat(link.getResolvedSubjectCpid()).isEqualTo("CPID-1");
        assertThat(link.getSupersededBy()).isNull();

        var episode = episodeRepo.findByEpisodeIdAndTenantId(episodeId, TENANT).orElseThrow();
        assertThat(episode.getSubjectCpid()).isEqualTo("CPID-1");
        assertThat(episode.getIdentityMode()).isEqualTo("KNOWN");
        assertThat(episode.getIdentityResolvedAt()).isNotNull();
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a correction supersedes the prior link — both remain readable, nothing is deleted")
    void correctionSupersedesNotOverwrites() {
        var first = service.linkIdentity(TENANT, episodeId, "CPID-WRONG", "RELATIVE", null, null, "nurse-A");
        var second = service.linkIdentity(TENANT, episodeId, "CPID-CORRECT", "DOCUMENT", "passport-9", null, "nurse-B");

        var history = service.history(TENANT, episodeId);
        assertThat(history).hasSize(2);

        var reloadedFirst = linkRepo.findById(first.getLinkId()).orElseThrow();
        assertThat(reloadedFirst.getSupersededBy()).isEqualTo(second.getLinkId());
        assertThat(reloadedFirst.getResolvedSubjectCpid()).isEqualTo("CPID-WRONG");

        var active = linkRepo.findByTenantIdAndEpisodeIdAndSupersededByIsNull(TENANT, episodeId).orElseThrow();
        assertThat(active.getLinkId()).isEqualTo(second.getLinkId());

        var episode = episodeRepo.findByEpisodeIdAndTenantId(episodeId, TENANT).orElseThrow();
        assertThat(episode.getSubjectCpid()).isEqualTo("CPID-CORRECT");
    }

    @Test
    @DisplayName("a VITO merge correlation id is retained when supplied")
    void vitoMergeIdRetained() {
        var link = service.linkIdentity(TENANT, episodeId, "CPID-1", "BIOMETRIC", null, "merge-abc", "nurse-A");
        assertThat(link.getVitoMergeId()).isEqualTo("merge-abc");
    }

    public static final class InMemoryIdentityLinkRepo implements EmergencyIdentityLinkRepository {
        final Map<UUID, EmergencyIdentityLinkEntity> store = new LinkedHashMap<>();

        @Override public Optional<EmergencyIdentityLinkEntity> findByTenantIdAndEpisodeIdAndSupersededByIsNull(
                UUID tenantId, UUID episodeId) {
            return store.values().stream()
                    .filter(l -> l.getTenantId().equals(tenantId) && l.getEpisodeId().equals(episodeId)
                            && l.getSupersededBy() == null)
                    .findFirst();
        }
        @Override public List<EmergencyIdentityLinkEntity> findByTenantIdAndEpisodeIdOrderByResolvedAtDesc(
                UUID tenantId, UUID episodeId) {
            List<EmergencyIdentityLinkEntity> out = new ArrayList<>();
            for (var l : store.values()) {
                if (l.getTenantId().equals(tenantId) && l.getEpisodeId().equals(episodeId)) out.add(l);
            }
            out.sort((a, b) -> b.getResolvedAt().compareTo(a.getResolvedAt()));
            return out;
        }
        @Override public <S extends EmergencyIdentityLinkEntity> S save(S e) {
            boolean isNew = e.getLinkId() == null || !store.containsKey(e.getLinkId());
            if (isNew && e.getSupersededBy() == null
                    && findByTenantIdAndEpisodeIdAndSupersededByIsNull(e.getTenantId(), e.getEpisodeId()).isPresent()) {
                throw new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_eil_active_per_episode\"");
            }
            if (e.getLinkId() == null) e.setLinkId(UUID.randomUUID());
            store.put(e.getLinkId(), e);
            return e;
        }
        @Override public <S extends EmergencyIdentityLinkEntity> S saveAndFlush(S e) { return save(e); }
        @Override public void flush() { }
        @Override public <S extends EmergencyIdentityLinkEntity> List<S> saveAllAndFlush(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<EmergencyIdentityLinkEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { throw new UnsupportedOperationException(); }
        @Override public EmergencyIdentityLinkEntity getOne(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyIdentityLinkEntity getById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public EmergencyIdentityLinkEntity getReferenceById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyIdentityLinkEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyIdentityLinkEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyIdentityLinkEntity> List<S> saveAll(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public List<EmergencyIdentityLinkEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public List<EmergencyIdentityLinkEntity> findAllById(Iterable<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public Optional<EmergencyIdentityLinkEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(EmergencyIdentityLinkEntity e) { store.remove(e.getLinkId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends EmergencyIdentityLinkEntity> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { store.clear(); }
        @Override public List<EmergencyIdentityLinkEntity> findAll(org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<EmergencyIdentityLinkEntity> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyIdentityLinkEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyIdentityLinkEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyIdentityLinkEntity> long count(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyIdentityLinkEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends EmergencyIdentityLinkEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }
}
