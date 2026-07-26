package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReproductiveIntentionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReproductiveIntentionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproductive intention: recorded, never inferred, and never flattened.
 */
class ReproductiveIntentionServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String SUBJECT = "CPID-1";

    private FakeIntentions repo;
    private ReproductiveIntentionService service;

    @BeforeEach
    void setUp() {
        repo = new FakeIntentions();
        service = new ReproductiveIntentionService(repo);
    }

    private ReproductiveIntentionService.RecordIntentionCommand cmd(String intention, Integer months,
                                                                    String offlineId) {
        return new ReproductiveIntentionService.RecordIntentionCommand(
                TENANT, SUBJECT, null, null, null, intention, months, null,
                null, null, null, null, null, offlineId, "nurse-1");
    }

    @Test
    void recordingANewIntentionSupersedesRatherThanOverwrites() {
        // She changed her mind, and that she changed it is clinical information about the
        // conversation to have next.
        var first = service.record(cmd(ReproductiveIntentionEntity.WANTS_PREGNANCY_LATER, 24, null));
        var second = service.record(cmd(ReproductiveIntentionEntity.WANTS_PREGNANCY_NOW, null, null));

        assertEquals(ReproductiveIntentionEntity.STATUS_SUPERSEDED,
                repo.byId.get(first.getIntentionId()).getStatus());
        assertEquals(second.getIntentionId(), repo.byId.get(first.getIntentionId()).getSupersededBy());
        assertEquals(second.getIntentionId(), service.current(TENANT, SUBJECT).orElseThrow().getIntentionId());
        assertEquals(2, service.history(TENANT, SUBJECT).size());
    }

    @Test
    void nobodyHavingAskedIsNull_notAPreference() {
        // Routing someone into contraceptive counselling on the strength of silence is how a
        // service becomes coercive.
        assertNull(service.wantsPregnancy(TENANT, SUBJECT));
        assertTrue(service.current(TENANT, SUBJECT).isEmpty());
    }

    @Test
    void undecidedAndDeclinedAreNeitherYesNorNo() {
        service.record(cmd(ReproductiveIntentionEntity.UNDECIDED, null, null));
        assertNull(service.wantsPregnancy(TENANT, SUBJECT));

        service.record(cmd(ReproductiveIntentionEntity.DECLINED_TO_STATE, null, null));
        assertNull(service.wantsPregnancy(TENANT, SUBJECT),
                "declining to answer is a choice she made, not an absence of one");
    }

    @Test
    void wantingAPregnancyLaterIsNotWantingOneNow() {
        service.record(cmd(ReproductiveIntentionEntity.WANTS_PREGNANCY_LATER, 36, null));
        assertNull(service.wantsPregnancy(TENANT, SUBJECT));
    }

    @Test
    void theTwoUnambiguousAnswersResolve() {
        service.record(cmd(ReproductiveIntentionEntity.WANTS_PREGNANCY_NOW, null, null));
        assertEquals(Boolean.TRUE, service.wantsPregnancy(TENANT, SUBJECT));

        service.record(cmd(ReproductiveIntentionEntity.WANTS_NO_MORE_CHILDREN, null, null));
        assertEquals(Boolean.FALSE, service.wantsPregnancy(TENANT, SUBJECT));
    }

    @Test
    void aReplayedOfflinePacketDoesNotLookLikeAChangeOfMind() {
        // Superseding an intention with an identical copy of itself would leave the history saying
        // she reconsidered and arrived at the same answer.
        var first = service.record(cmd(ReproductiveIntentionEntity.WANTS_PREGNANCY_NOW, null, "OFF-1"));
        var replay = service.record(cmd(ReproductiveIntentionEntity.WANTS_PREGNANCY_NOW, null, "OFF-1"));

        assertEquals(first.getIntentionId(), replay.getIntentionId());
        assertEquals(1, repo.byId.size());
        assertEquals(ReproductiveIntentionEntity.STATUS_CURRENT,
                repo.byId.get(first.getIntentionId()).getStatus());
    }

    @Test
    void unstatedSecondaryAnswersRecordAsNotAssessed() {
        var recorded = service.record(cmd(ReproductiveIntentionEntity.UNDECIDED, null, null));
        assertEquals(ReproductiveIntentionEntity.NOT_ASSESSED, recorded.getPartnerIntention());
        assertEquals(ReproductiveIntentionEntity.NOT_ASSESSED, recorded.getUnmetNeed());
        assertEquals(ReproductiveIntentionEntity.NOT_ASSESSED, recorded.getFertilityConcern());
    }

    private static final class FakeIntentions implements ReproductiveIntentionRepository {
        final java.util.LinkedHashMap<UUID, ReproductiveIntentionEntity> byId = new java.util.LinkedHashMap<>();

        @Override public Optional<ReproductiveIntentionEntity> findCurrent(UUID t, String s) {
            return byId.values().stream()
                    .filter(i -> i.getTenantId().equals(t) && i.getSubjectCpid().equals(s))
                    .filter(i -> ReproductiveIntentionEntity.STATUS_CURRENT.equals(i.getStatus()))
                    .findFirst();
        }
        @Override public List<ReproductiveIntentionEntity> history(UUID t, String s) {
            return byId.values().stream()
                    .filter(i -> i.getTenantId().equals(t) && i.getSubjectCpid().equals(s)).toList();
        }
        @Override public Optional<ReproductiveIntentionEntity> findByTenantIdAndClientOfflineId(UUID t, String o) {
            return byId.values().stream()
                    .filter(i -> o != null && o.equals(i.getClientOfflineId())).findFirst();
        }
        @Override public <S extends ReproductiveIntentionEntity> S save(S e) { byId.put(e.getIntentionId(), e); return e; }
        @Override public Optional<ReproductiveIntentionEntity> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
        @Override public void flush() { }
        @Override public <S extends ReproductiveIntentionEntity> S saveAndFlush(S e) { return save(e); }
        @Override public <S extends ReproductiveIntentionEntity> List<S> saveAllAndFlush(Iterable<S> e) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<ReproductiveIntentionEntity> e) { }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> i) { }
        @Override public void deleteAllInBatch() { }
        @Override public ReproductiveIntentionEntity getOne(UUID id) { return byId.get(id); }
        @Override public ReproductiveIntentionEntity getById(UUID id) { return byId.get(id); }
        @Override public ReproductiveIntentionEntity getReferenceById(UUID id) { return byId.get(id); }
        @Override public <S extends ReproductiveIntentionEntity> List<S> findAll(org.springframework.data.domain.Example<S> e) { return List.of(); }
        @Override public <S extends ReproductiveIntentionEntity> List<S> findAll(org.springframework.data.domain.Example<S> e, org.springframework.data.domain.Sort s) { return List.of(); }
        @Override public <S extends ReproductiveIntentionEntity> List<S> saveAll(Iterable<S> e) { return List.of(); }
        @Override public List<ReproductiveIntentionEntity> findAll() { return List.copyOf(byId.values()); }
        @Override public List<ReproductiveIntentionEntity> findAllById(Iterable<UUID> i) { return List.of(); }
        @Override public long count() { return byId.size(); }
        @Override public void deleteById(UUID id) { }
        @Override public void delete(ReproductiveIntentionEntity e) { }
        @Override public void deleteAllById(Iterable<? extends UUID> i) { }
        @Override public void deleteAll(Iterable<? extends ReproductiveIntentionEntity> e) { }
        @Override public void deleteAll() { byId.clear(); }
        @Override public List<ReproductiveIntentionEntity> findAll(org.springframework.data.domain.Sort s) { return List.of(); }
        @Override public org.springframework.data.domain.Page<ReproductiveIntentionEntity> findAll(org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public boolean existsById(UUID id) { return byId.containsKey(id); }
        @Override public <S extends ReproductiveIntentionEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> e) { return Optional.empty(); }
        @Override public <S extends ReproductiveIntentionEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> e, org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends ReproductiveIntentionEntity> long count(org.springframework.data.domain.Example<S> e) { return 0; }
        @Override public <S extends ReproductiveIntentionEntity> boolean exists(org.springframework.data.domain.Example<S> e) { return false; }
        @Override public <S extends ReproductiveIntentionEntity, R> R findBy(org.springframework.data.domain.Example<S> e, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { return null; }
    }
}
