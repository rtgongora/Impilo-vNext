package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.persistence.entity.FetusEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.FetusRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.PregnancyEpisodeRepository;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fetus records and fetus-to-newborn matching.
 *
 * <p>The two properties under test are the ones a multiple pregnancy depends on: discordant
 * outcomes must be expressible, and a match must be asserted rather than guessed.
 */
class FetusServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String MOTHER = "CPID-MOTHER-1";

    private FakeFetuses fetuses;
    private FakeEpisodes episodes;
    private FetusService service;
    private UUID pregnancyId;

    @BeforeEach
    void setUp() {
        fetuses = new FakeFetuses();
        episodes = new FakeEpisodes();
        service = new FetusService(fetuses, episodes);
        pregnancyId = UUID.randomUUID();
        PregnancyEpisodeEntity episode = new PregnancyEpisodeEntity();
        episode.setPregnancyEpisodeId(pregnancyId);
        episode.setTenantId(TENANT);
        episode.setSubjectCpid(MOTHER);
        episode.setPlurality(2);
        episode.setStatus(PregnancyEpisodeEntity.STATUS_ONGOING);
        episodes.save(episode);
    }

    @Test
    void theFetusRecordHasNoWayToHoldAnIdentity() {
        // The structural guarantee, asserted by reflection because a comment is not an enforcement
        // mechanism. A field able to hold a CPID is eventually filled by something — an integration,
        // a form that needed a key, a migration that wanted a join — and a fetus is not a
        // registered person. Identity begins at the newborn birth record.
        List<String> identityish = new ArrayList<>();
        for (Field field : FetusEntity.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            boolean isMother = name.contains("mother");
            boolean isNewbornLink = name.contains("newbornbirthrecord");
            if (!isMother && !isNewbornLink
                    && (name.contains("cpid") || name.equals("subjectid") || name.contains("healthid")
                        || name.contains("identity") || name.contains("nationalid"))) {
                identityish.add(field.getName());
            }
        }
        assertTrue(identityish.isEmpty(),
                "FetusEntity must have no identity field for the fetus; found: " + identityish);
    }

    @Test
    void aSingletonMatchesAutomaticallyAndSaysWhy() {
        UUID singleton = UUID.randomUUID();
        PregnancyEpisodeEntity e = new PregnancyEpisodeEntity();
        e.setPregnancyEpisodeId(singleton);
        e.setTenantId(TENANT);
        e.setSubjectCpid(MOTHER);
        e.setPlurality(1);
        episodes.save(e);

        FetusEntity fetus = service.record(TENANT, singleton, MOTHER, "A", "midwife-1");
        fetus.setOutcome(FetusEntity.OUTCOME_LIVE_BIRTH);
        fetuses.save(fetus);

        UUID birthRecord = UUID.randomUUID();
        var matched = service.autoMatchSingleton(TENANT, singleton, birthRecord, "midwife-1");

        assertTrue(matched.isPresent());
        assertEquals(FetusEntity.MATCH_SINGLETON_UNAMBIGUOUS, matched.get().getMatchBasis());
        assertEquals("CERTAIN", matched.get().getMatchConfidence());
        assertNotNull(matched.get().getMatchedAt());
        assertNotNull(matched.get().getMatchedBy());
    }

    @Test
    void aMultiplePregnancyIsNeverMatchedAutomatically() {
        // Birth order is the usual basis but is not always the antenatal labelling, and a
        // mismatched twin cannot be corrected later.
        service.record(TENANT, pregnancyId, MOTHER, "A", "midwife-1");
        service.record(TENANT, pregnancyId, MOTHER, "B", "midwife-1");

        assertTrue(service.autoMatchSingleton(TENANT, pregnancyId, UUID.randomUUID(), "midwife-1").isEmpty());
    }

    @Test
    void aMatchWithoutProvenanceIsRefused() {
        FetusEntity fetus = service.record(TENANT, pregnancyId, MOTHER, "A", "midwife-1");
        fetus.setOutcome(FetusEntity.OUTCOME_LIVE_BIRTH);
        fetuses.save(fetus);

        var thrown = assertThrows(FetusService.UnmatchableFetusException.class,
                () -> service.matchToNewborn(TENANT, fetus.getFetusId(), UUID.randomUUID(),
                        null, "CERTAIN", "midwife-1", null));
        assertTrue(thrown.getMessage().contains("cannot be corrected later"));
    }

    @Test
    void aFetusWithNoOutcomeHasNoBirthToMatch() {
        FetusEntity fetus = service.record(TENANT, pregnancyId, MOTHER, "A", "midwife-1");
        var thrown = assertThrows(FetusService.UnmatchableFetusException.class,
                () -> service.matchToNewborn(TENANT, fetus.getFetusId(), UUID.randomUUID(),
                        FetusEntity.MATCH_DELIVERY_ORDER, "CERTAIN", "midwife-1", null));
        assertTrue(thrown.getMessage().contains("no recorded outcome"));
    }

    @Test
    void oneBabyCannotBeClaimedByTwoFetuses() {
        FetusEntity a = service.record(TENANT, pregnancyId, MOTHER, "A", "midwife-1");
        FetusEntity b = service.record(TENANT, pregnancyId, MOTHER, "B", "midwife-1");
        a.setOutcome(FetusEntity.OUTCOME_LIVE_BIRTH);
        b.setOutcome(FetusEntity.OUTCOME_LIVE_BIRTH);
        fetuses.save(a);
        fetuses.save(b);

        UUID baby = UUID.randomUUID();
        service.matchToNewborn(TENANT, a.getFetusId(), baby,
                FetusEntity.MATCH_ATTENDANT_ASSERTION, "CERTAIN", "midwife-1", null);

        var thrown = assertThrows(FetusService.UnmatchableFetusException.class,
                () -> service.matchToNewborn(TENANT, b.getFetusId(), baby,
                        FetusEntity.MATCH_ATTENDANT_ASSERTION, "CERTAIN", "midwife-1", null));
        assertTrue(thrown.getMessage().contains("orphans"));
    }

    @Test
    void discordantTwinOutcomesProduceAMixedPregnancyOutcome() {
        // The reason fetuses are a table rather than columns. One liveborn, one stillborn: a
        // pregnancy-level outcome column would record whichever was entered last.
        FetusEntity a = service.record(TENANT, pregnancyId, MOTHER, "A", "midwife-1");
        FetusEntity b = service.record(TENANT, pregnancyId, MOTHER, "B", "midwife-1");
        a.setOutcome(FetusEntity.OUTCOME_LIVE_BIRTH);
        b.setOutcome(FetusEntity.OUTCOME_STILLBIRTH_INTRAPARTUM);
        fetuses.save(a);
        fetuses.save(b);

        assertEquals("MIXED_OUTCOME", service.derivePregnancyOutcome(pregnancyId).orElseThrow());
    }

    @Test
    void aPregnancyIsNotOverBecauseOneTwinHasBeenDelivered() {
        FetusEntity a = service.record(TENANT, pregnancyId, MOTHER, "A", "midwife-1");
        service.record(TENANT, pregnancyId, MOTHER, "B", "midwife-1");
        a.setOutcome(FetusEntity.OUTCOME_LIVE_BIRTH);
        fetuses.save(a);

        assertTrue(service.derivePregnancyOutcome(pregnancyId).isEmpty());
    }

    @Test
    void aVanishedTwinDoesNotReducePlurality() {
        // The record must keep saying the pregnancy began as a twin pregnancy: it changes how
        // everything measured afterwards is read.
        FetusEntity a = service.record(TENANT, pregnancyId, MOTHER, "A", "midwife-1");
        FetusEntity b = service.record(TENANT, pregnancyId, MOTHER, "B", "midwife-1");
        a.setOutcome(FetusEntity.OUTCOME_LIVE_BIRTH);
        fetuses.save(a);

        service.recordVanished(TENANT, b.getFetusId(), "sonographer-1");

        assertEquals(2, episodes.byId.get(pregnancyId).getPlurality());
        assertEquals("LIVE_BIRTH", service.derivePregnancyOutcome(pregnancyId).orElseThrow(),
                "a vanished twin does not make the outcome mixed");
    }

    @Test
    void recordingTheSameLabelTwiceReturnsTheSameFetus() {
        FetusEntity first = service.record(TENANT, pregnancyId, MOTHER, "A", "midwife-1");
        FetusEntity again = service.record(TENANT, pregnancyId, MOTHER, "A", "midwife-1");
        assertEquals(first.getFetusId(), again.getFetusId());
        assertEquals(1, fetuses.byId.size());
    }

    // ── fakes ────────────────────────────────────────────────────────────────

    private static final class FakeFetuses implements FetusRepository {
        final java.util.LinkedHashMap<UUID, FetusEntity> byId = new java.util.LinkedHashMap<>();

        @Override
        public List<FetusEntity> findByPregnancyEpisodeIdOrderByFetusLabelAsc(UUID id) {
            return byId.values().stream()
                    .filter(f -> f.getPregnancyEpisodeId().equals(id))
                    .sorted(java.util.Comparator.comparing(FetusEntity::getFetusLabel))
                    .toList();
        }

        @Override
        public Optional<FetusEntity> findByPregnancyEpisodeIdAndFetusLabel(UUID id, String label) {
            return byId.values().stream()
                    .filter(f -> f.getPregnancyEpisodeId().equals(id) && f.getFetusLabel().equals(label))
                    .findFirst();
        }

        @Override
        public Optional<FetusEntity> findByNewbornBirthRecordId(UUID newbornId) {
            return byId.values().stream()
                    .filter(f -> newbornId.equals(f.getNewbornBirthRecordId()))
                    .findFirst();
        }

        @Override public <S extends FetusEntity> S save(S e) { byId.put(e.getFetusId(), e); return e; }
        @Override public Optional<FetusEntity> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
        @Override public void flush() { }
        @Override public <S extends FetusEntity> S saveAndFlush(S e) { return save(e); }
        @Override public <S extends FetusEntity> List<S> saveAllAndFlush(Iterable<S> e) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<FetusEntity> e) { }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override public FetusEntity getOne(UUID id) { return byId.get(id); }
        @Override public FetusEntity getById(UUID id) { return byId.get(id); }
        @Override public FetusEntity getReferenceById(UUID id) { return byId.get(id); }
        @Override public <S extends FetusEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex) { return List.of(); }
        @Override public <S extends FetusEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { return List.of(); }
        @Override public <S extends FetusEntity> List<S> saveAll(Iterable<S> e) { return List.of(); }
        @Override public List<FetusEntity> findAll() { return List.copyOf(byId.values()); }
        @Override public List<FetusEntity> findAllById(Iterable<UUID> ids) { return List.of(); }
        @Override public long count() { return byId.size(); }
        @Override public void deleteById(UUID id) { byId.remove(id); }
        @Override public void delete(FetusEntity e) { }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { }
        @Override public void deleteAll(Iterable<? extends FetusEntity> e) { }
        @Override public void deleteAll() { byId.clear(); }
        @Override public List<FetusEntity> findAll(org.springframework.data.domain.Sort s) { return List.of(); }
        @Override public org.springframework.data.domain.Page<FetusEntity> findAll(org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public boolean existsById(UUID id) { return byId.containsKey(id); }
        @Override public <S extends FetusEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { return Optional.empty(); }
        @Override public <S extends FetusEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends FetusEntity> long count(org.springframework.data.domain.Example<S> ex) { return 0; }
        @Override public <S extends FetusEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { return false; }
        @Override public <S extends FetusEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> fn) { return null; }
    }

    private static final class FakeEpisodes implements PregnancyEpisodeRepository {
        final java.util.LinkedHashMap<UUID, PregnancyEpisodeEntity> byId = new java.util.LinkedHashMap<>();
        @Override public Optional<PregnancyEpisodeEntity> findOngoing(UUID t, String s) { return Optional.empty(); }
        @Override public List<PregnancyEpisodeEntity> findBySubject(UUID t, String s) { return List.of(); }
        @Override public Optional<PregnancyEpisodeEntity> findByTenantIdAndClientOfflineId(UUID t, String o) { return Optional.empty(); }
        @Override public List<PregnancyEpisodeEntity> findCoveringDate(UUID t, String s, LocalDate on, LocalDate g) { return List.of(); }
        @Override public <S extends PregnancyEpisodeEntity> S save(S e) { byId.put(e.getPregnancyEpisodeId(), e); return e; }
        @Override public Optional<PregnancyEpisodeEntity> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
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
        @Override public <S extends PregnancyEpisodeEntity> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { return List.of(); }
        @Override public <S extends PregnancyEpisodeEntity> List<S> saveAll(Iterable<S> e) { return List.of(); }
        @Override public List<PregnancyEpisodeEntity> findAll() { return List.copyOf(byId.values()); }
        @Override public List<PregnancyEpisodeEntity> findAllById(Iterable<UUID> ids) { return List.of(); }
        @Override public long count() { return byId.size(); }
        @Override public void deleteById(UUID id) { }
        @Override public void delete(PregnancyEpisodeEntity e) { }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { }
        @Override public void deleteAll(Iterable<? extends PregnancyEpisodeEntity> e) { }
        @Override public void deleteAll() { byId.clear(); }
        @Override public List<PregnancyEpisodeEntity> findAll(org.springframework.data.domain.Sort s) { return List.of(); }
        @Override public org.springframework.data.domain.Page<PregnancyEpisodeEntity> findAll(org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public boolean existsById(UUID id) { return byId.containsKey(id); }
        @Override public <S extends PregnancyEpisodeEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { return Optional.empty(); }
        @Override public <S extends PregnancyEpisodeEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends PregnancyEpisodeEntity> long count(org.springframework.data.domain.Example<S> ex) { return 0; }
        @Override public <S extends PregnancyEpisodeEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { return false; }
        @Override public <S extends PregnancyEpisodeEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> fn) { return null; }
    }
}
