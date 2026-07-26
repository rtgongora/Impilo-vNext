package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyDatingRevisionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.PregnancyDatingRevisionRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.PregnancyEpisodeRepository;
import zw.gov.mohcc.impilo.reproductive.dating.DatingBasis;
import zw.gov.mohcc.impilo.reproductive.dating.DatingMethod;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opening, dating and finding a pregnancy episode.
 *
 * <p>Uses hand-written fakes rather than mocks for the repositories. A mock that returns whatever it
 * was told to return proves the shape of the code; these keep enough state to answer "is there
 * already an ongoing pregnancy for this woman", which is the question every duplicate check turns
 * on — and the paediatric pack's NewbornEpisodeOpener defect is the standing reminder that a mock
 * which throws proves the shape of the code, not the behaviour of the system.
 */
class PregnancyEpisodeServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String SUBJECT = "CPID-WOMAN-1";

    private FakeEpisodes episodes;
    private FakeRevisions revisions;
    private PregnancyEpisodeService service;

    @BeforeEach
    void setUp() {
        episodes = new FakeEpisodes();
        revisions = new FakeRevisions();
        service = new PregnancyEpisodeService(episodes, revisions);
    }

    private PregnancyEpisodeService.OpenPregnancyCommand command(List<DatingBasis> bases, String offlineId) {
        return new PregnancyEpisodeService.OpenPregnancyCommand(
                TENANT, SUBJECT, UUID.randomUUID(), "JRN-1", "ENC-1",
                bases, LocalDate.of(2026, 2, 1), null, "URINE_HCG",
                null, null, offlineId, null, "midwife-1");
    }

    @Test
    void openingAPregnancyStampsTheDateAndItsBasis() {
        var episode = service.open(command(
                List.of(DatingBasis.fromLmp(LocalDate.of(2026, 1, 1), true)), null));

        assertEquals(LocalDate.of(2026, 10, 8), episode.getEstimatedDeliveryDate());
        assertEquals(LocalDate.of(2026, 1, 1), episode.getPregnancyStartDate());
        assertEquals(DatingMethod.LMP_CERTAIN.name(), episode.getDatingMethod());
        assertEquals("MODERATE", episode.getDatingConfidence());
        assertEquals(PregnancyEpisodeEntity.STATUS_ONGOING, episode.getStatus());
        // The dating decision is written down, not just its result.
        assertEquals(1, revisions.saved.size());
        assertEquals(1, revisions.saved.get(0).getRevisionNumber());
    }

    @Test
    void theImmutableStartDateIsSetOnceSoARedatingCannotMoveTheDuplicateKey() {
        var episode = service.open(command(
                List.of(DatingBasis.fromLmp(LocalDate.of(2026, 1, 1), true)), null));

        assertEquals(episode.getPregnancyStartDate(), episode.getInitialPregnancyStartDate());

        // An early scan supersedes the LMP; the working start date moves, the booking key does not.
        service.revise(TENANT, episode.getPregnancyEpisodeId(),
                DatingBasis.fromUltrasound(LocalDate.of(2026, 4, 1), 70, "USS-1"),
                LocalDate.of(2026, 4, 1), "midwife-1");

        var after = episodes.byId.get(episode.getPregnancyEpisodeId());
        assertNotEquals(after.getPregnancyStartDate(), after.getInitialPregnancyStartDate());
        assertEquals(LocalDate.of(2026, 1, 1), after.getInitialPregnancyStartDate());
    }

    @Test
    void aSecondOngoingPregnancyIsRefusedWithTheExistingEpisode_notA500() {
        // A 500 here loses a booking captured in a clinic with no network, which is exactly the
        // case the offline mechanism exists to serve. The caller needs the existing id to reconcile.
        var first = service.open(command(
                List.of(DatingBasis.fromLmp(LocalDate.of(2026, 1, 1), true)), null));

        var thrown = assertThrows(PregnancyEpisodeService.DuplicatePregnancyException.class,
                () -> service.open(command(
                        List.of(DatingBasis.fromLmp(LocalDate.of(2026, 1, 20), true)), null)));

        assertEquals(first.getPregnancyEpisodeId(), thrown.existingEpisodeId());
        assertTrue(thrown.getMessage().contains("heterotopic"),
                "the message should say why one episode is correct, not just refuse");
    }

    @Test
    void aReplayedOfflinePacketReturnsTheSameEpisodeRatherThanASecond() {
        var first = service.open(command(
                List.of(DatingBasis.fromLmp(LocalDate.of(2026, 1, 1), true)), "OFFLINE-1"));
        var replay = service.open(command(
                List.of(DatingBasis.fromLmp(LocalDate.of(2026, 1, 1), true)), "OFFLINE-1"));

        assertEquals(first.getPregnancyEpisodeId(), replay.getPregnancyEpisodeId());
        assertEquals(1, episodes.byId.size());
    }

    @Test
    void anUndatablePregnancyIsRefusedRatherThanDefaulted() {
        // Without a start date every gestation-scoped rule downstream would silently fail to apply
        // rather than reporting that it could not.
        var thrown = assertThrows(PregnancyEpisodeService.UndatablePregnancyException.class,
                () -> service.open(command(List.of(), null)));
        assertTrue(thrown.getMessage().contains("silently"));
    }

    @Test
    void aRejectedDatingBasisIsStillRecordedAsARevision() {
        var episode = service.open(command(
                List.of(DatingBasis.fromLmp(LocalDate.of(2026, 1, 1), true)), null));
        LocalDate edd = episode.getEstimatedDeliveryDate();

        // A 30-week scan measuring 4 weeks small. It must not redate.
        var revised = service.revise(TENANT, episode.getPregnancyEpisodeId(),
                DatingBasis.fromUltrasound(LocalDate.of(2026, 9, 1), 210, "USS-3"),
                LocalDate.of(2026, 9, 1), "doctor-1");

        assertFalse(revised.redatingApplied());
        assertEquals(edd, episodes.byId.get(episode.getPregnancyEpisodeId()).getEstimatedDeliveryDate());
        assertEquals(2, revisions.saved.size(), "the rejected basis is still written down");
        PregnancyDatingRevisionEntity rejected = revisions.saved.get(1);
        assertFalse(rejected.getRedatingApplied());
        assertNotNull(rejected.getRationale());
        assertTrue(rejected.getRationale().contains("measures size"));
    }

    @Test
    void pregnantDerivesTrueButNeverFalse() {
        // "No pregnancy has been recorded" and "she is not pregnant" are different statements, and
        // only the first is one this system can make.
        assertNull(service.pregnant(TENANT, SUBJECT));
        service.open(command(List.of(DatingBasis.fromLmp(LocalDate.of(2026, 1, 1), true)), null));
        assertEquals(Boolean.TRUE, service.pregnant(TENANT, SUBJECT));
        assertNull(service.pregnant(TENANT, "CPID-SOMEONE-ELSE"));
    }

    @Test
    void gestationalAgeComesFromTheStoredDateSoNothingElseHasToRecomputeIt() {
        service.open(command(List.of(DatingBasis.fromLmp(LocalDate.of(2026, 1, 1), true)), null));
        assertEquals(228, service.gestationalAgeDays(TENANT, SUBJECT, LocalDate.of(2026, 8, 17)));
        assertNull(service.gestationalAgeDays(TENANT, "CPID-SOMEONE-ELSE", LocalDate.of(2026, 8, 17)));
    }

    @Test
    void anAmbiguousLinkageLeavesTheRecordUnlinkedRatherThanGuessing() {
        // Attaching a partograph to the wrong pregnancy is worse than attaching it to none: the
        // wrong one is invisible and the right one looks like it was never charted.
        service.open(command(List.of(DatingBasis.fromLmp(LocalDate.of(2026, 1, 1), true)), null));
        // Force a second overlapping episode past the service's own guard, as a corrupted history
        // would look.
        PregnancyEpisodeEntity overlapping = new PregnancyEpisodeEntity();
        overlapping.setPregnancyEpisodeId(UUID.randomUUID());
        overlapping.setTenantId(TENANT);
        overlapping.setSubjectCpid(SUBJECT);
        overlapping.setStatus(PregnancyEpisodeEntity.STATUS_ONGOING);
        overlapping.setPregnancyStartDate(LocalDate.of(2026, 1, 15));
        overlapping.setInitialPregnancyStartDate(LocalDate.of(2026, 1, 15));
        episodes.save(overlapping);

        assertTrue(service.episodeCovering(TENANT, SUBJECT, LocalDate.of(2026, 6, 1)).isEmpty());
    }

    @Test
    void aRecordFromJustAfterDeliveryStillFindsItsPregnancy() {
        var episode = service.open(command(
                List.of(DatingBasis.fromLmp(LocalDate.of(2026, 1, 1), true)), null));
        episode.setStatus(PregnancyEpisodeEntity.STATUS_COMPLETED);
        episode.setEndedOn(LocalDate.of(2026, 10, 1));
        episodes.save(episode);

        // A delivery recorded three days later, and a postnatal note ten days later.
        assertTrue(service.episodeCovering(TENANT, SUBJECT, LocalDate.of(2026, 10, 4)).isPresent());
        assertTrue(service.episodeCovering(TENANT, SUBJECT, LocalDate.of(2026, 10, 11)).isPresent());
        // Two months later belongs to no pregnancy.
        assertTrue(service.episodeCovering(TENANT, SUBJECT, LocalDate.of(2026, 12, 20)).isEmpty());
    }

    // ── fakes ────────────────────────────────────────────────────────────────

    private static final class FakeEpisodes implements PregnancyEpisodeRepository {
        final java.util.LinkedHashMap<UUID, PregnancyEpisodeEntity> byId = new java.util.LinkedHashMap<>();

        @Override
        public Optional<PregnancyEpisodeEntity> findOngoing(UUID tenantId, String subjectCpid) {
            return byId.values().stream()
                    .filter(e -> e.getTenantId().equals(tenantId))
                    .filter(e -> e.getSubjectCpid().equals(subjectCpid))
                    .filter(PregnancyEpisodeEntity::ongoing)
                    .findFirst();
        }

        @Override
        public List<PregnancyEpisodeEntity> findBySubject(UUID tenantId, String subjectCpid) {
            return byId.values().stream()
                    .filter(e -> e.getTenantId().equals(tenantId))
                    .filter(e -> e.getSubjectCpid().equals(subjectCpid))
                    .toList();
        }

        @Override
        public Optional<PregnancyEpisodeEntity> findByTenantIdAndClientOfflineId(UUID tenantId, String offlineId) {
            return byId.values().stream()
                    .filter(e -> e.getTenantId().equals(tenantId))
                    .filter(e -> offlineId != null && offlineId.equals(e.getClientOfflineId()))
                    .findFirst();
        }

        @Override
        public List<PregnancyEpisodeEntity> findCoveringDate(UUID tenantId, String subjectCpid,
                                                             LocalDate on, LocalDate graceFloor) {
            List<PregnancyEpisodeEntity> out = new ArrayList<>();
            for (PregnancyEpisodeEntity e : byId.values()) {
                if (!e.getTenantId().equals(tenantId) || !e.getSubjectCpid().equals(subjectCpid)) {
                    continue;
                }
                if (PregnancyEpisodeEntity.STATUS_ENTERED_IN_ERROR.equals(e.getStatus())) {
                    continue;
                }
                if (e.getPregnancyStartDate() != null && !e.getPregnancyStartDate().isAfter(on)
                        && (e.getEndedOn() == null || !e.getEndedOn().isBefore(graceFloor))) {
                    out.add(e);
                }
            }
            return out;
        }

        @Override
        public <S extends PregnancyEpisodeEntity> S save(S entity) {
            byId.put(entity.getPregnancyEpisodeId(), entity);
            return entity;
        }

        @Override
        public Optional<PregnancyEpisodeEntity> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        // ── unused JpaRepository surface ──
        @Override public void flush() { }
        @Override public <S extends PregnancyEpisodeEntity> S saveAndFlush(S e) { return save(e); }
        @Override public <S extends PregnancyEpisodeEntity> List<S> saveAllAndFlush(Iterable<S> e) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<PregnancyEpisodeEntity> e) { }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override public PregnancyEpisodeEntity getOne(UUID id) { return byId.get(id); }
        @Override public PregnancyEpisodeEntity getById(UUID id) { return byId.get(id); }
        @Override public PregnancyEpisodeEntity getReferenceById(UUID id) { return byId.get(id); }
        @Override public <S extends PregnancyEpisodeEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { return List.of(); }
        @Override public <S extends PregnancyEpisodeEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public <S extends PregnancyEpisodeEntity> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public List<PregnancyEpisodeEntity> findAll() { return List.copyOf(byId.values()); }
        @Override public List<PregnancyEpisodeEntity> findAllById(Iterable<UUID> ids) { return List.of(); }
        @Override public long count() { return byId.size(); }
        @Override public void deleteById(UUID id) { byId.remove(id); }
        @Override public void delete(PregnancyEpisodeEntity entity) { }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { }
        @Override public void deleteAll(Iterable<? extends PregnancyEpisodeEntity> entities) { }
        @Override public void deleteAll() { byId.clear(); }
        @Override public List<PregnancyEpisodeEntity> findAll(org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public org.springframework.data.domain.Page<PregnancyEpisodeEntity> findAll(org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public boolean existsById(UUID id) { return byId.containsKey(id); }
        @Override public <S extends PregnancyEpisodeEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { return Optional.empty(); }
        @Override public <S extends PregnancyEpisodeEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends PregnancyEpisodeEntity> long count(org.springframework.data.domain.Example<S> ex) { return 0; }
        @Override public <S extends PregnancyEpisodeEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { return false; }
        @Override public <S extends PregnancyEpisodeEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> fn) { return null; }
    }

    private static final class FakeRevisions implements PregnancyDatingRevisionRepository {
        final List<PregnancyDatingRevisionEntity> saved = new ArrayList<>();

        @Override
        public List<PregnancyDatingRevisionEntity> findByPregnancyEpisodeIdOrderByRevisionNumberDesc(UUID id) {
            return saved.stream().filter(r -> r.getPregnancyEpisodeId().equals(id)).toList();
        }

        @Override
        public int countByPregnancyEpisodeId(UUID id) {
            return (int) saved.stream().filter(r -> r.getPregnancyEpisodeId().equals(id)).count();
        }

        @Override
        public <S extends PregnancyDatingRevisionEntity> S save(S entity) {
            saved.add(entity);
            return entity;
        }

        @Override public void flush() { }
        @Override public <S extends PregnancyDatingRevisionEntity> S saveAndFlush(S e) { return save(e); }
        @Override public <S extends PregnancyDatingRevisionEntity> List<S> saveAllAndFlush(Iterable<S> e) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<PregnancyDatingRevisionEntity> e) { }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override public PregnancyDatingRevisionEntity getOne(UUID id) { return null; }
        @Override public PregnancyDatingRevisionEntity getById(UUID id) { return null; }
        @Override public PregnancyDatingRevisionEntity getReferenceById(UUID id) { return null; }
        @Override public <S extends PregnancyDatingRevisionEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { return List.of(); }
        @Override public <S extends PregnancyDatingRevisionEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public <S extends PregnancyDatingRevisionEntity> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public List<PregnancyDatingRevisionEntity> findAll() { return List.copyOf(saved); }
        @Override public List<PregnancyDatingRevisionEntity> findAllById(Iterable<UUID> ids) { return List.of(); }
        @Override public long count() { return saved.size(); }
        @Override public void deleteById(UUID id) { }
        @Override public void delete(PregnancyDatingRevisionEntity entity) { }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { }
        @Override public void deleteAll(Iterable<? extends PregnancyDatingRevisionEntity> entities) { }
        @Override public void deleteAll() { saved.clear(); }
        @Override public List<PregnancyDatingRevisionEntity> findAll(org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public org.springframework.data.domain.Page<PregnancyDatingRevisionEntity> findAll(org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public Optional<PregnancyDatingRevisionEntity> findById(UUID id) { return Optional.empty(); }
        @Override public boolean existsById(UUID id) { return false; }
        @Override public <S extends PregnancyDatingRevisionEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { return Optional.empty(); }
        @Override public <S extends PregnancyDatingRevisionEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends PregnancyDatingRevisionEntity> long count(org.springframework.data.domain.Example<S> ex) { return 0; }
        @Override public <S extends PregnancyDatingRevisionEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { return false; }
        @Override public <S extends PregnancyDatingRevisionEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> fn) { return null; }
    }
}
