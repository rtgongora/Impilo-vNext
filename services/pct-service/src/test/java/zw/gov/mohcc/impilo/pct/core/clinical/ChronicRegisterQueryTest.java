package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProgrammeEnrolmentEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProgrammeEnrolmentRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chronic-disease register read — the first cohort query in this estate.
 *
 * <p>Executes the JPQL against a real persistence context rather than asserting it compiles. Every
 * other problem and enrolment query here is subject-scoped; this one leads with the facility, which
 * is a different index and a different question, and it is worth proving it runs.</p>
 *
 * <p>{@code @Transactional} so each method rolls back. Without it the saves persist across methods
 * and the counts accumulate — the failure mode that made {@link ProgrammeCohortQueryTest} fail on its
 * first run, where the defect was in the test and not the query.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChronicRegisterQueryTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000012");
    private static final LocalDate ENROLLED = LocalDate.of(2026, 1, 1);

    @Autowired
    private ProgrammeEnrolmentRepository repository;

    private ProgrammeEnrolmentEntity save(UUID tenant, String programme, String status, String facility,
                                          String controlStatus, LocalDate assessedOn) {
        ProgrammeEnrolmentEntity e = new ProgrammeEnrolmentEntity();
        e.setEnrolmentId(UUID.randomUUID());
        e.setTenantId(tenant);
        e.setSubjectCpid("cpid-" + UUID.randomUUID());
        e.setProgramme(programme);
        e.setStatus(status);
        e.setAnchorProblemId(UUID.randomUUID());
        e.setEnrolledOn(ENROLLED);
        e.setManagingFacilityId(facility);
        e.setControlStatus(controlStatus);
        e.setControlAssessedOn(assessedOn);
        e.setCreatedBy("test");
        return repository.save(e);
    }

    private List<ProgrammeEnrolmentEntity> register(String programme, String facility) {
        return repository.register(TENANT, programme, facility,
                ProgrammeVocabulary.OPEN_ENROLMENT_STATUSES);
    }

    @Test
    void theRegisterRunsAndIsScopedToOneProgrammeAtOneFacility() {
        // That this executes at all is half the point: the cohort JPQL is now proven to parse and run.
        save(TENANT, ProgrammeVocabulary.HYPERTENSION, "ON_TREATMENT", "fac-1", "CONTROLLED", ENROLLED.plusDays(10));
        save(TENANT, ProgrammeVocabulary.HYPERTENSION, "ON_TREATMENT", "fac-2", "CONTROLLED", ENROLLED.plusDays(10));
        save(TENANT, ProgrammeVocabulary.DIABETES, "ON_TREATMENT", "fac-1", "CONTROLLED", ENROLLED.plusDays(10));

        assertThat(register(ProgrammeVocabulary.HYPERTENSION, "fac-1")).hasSize(1);
        assertThat(register(ProgrammeVocabulary.HYPERTENSION, null)).hasSize(2);
    }

    @Test
    void anotherTenantsRegisterNeverLeaksIntoThisOne() {
        save(TENANT, ProgrammeVocabulary.DIABETES, "ON_TREATMENT", "fac-1", null, null);
        save(OTHER_TENANT, ProgrammeVocabulary.DIABETES, "ON_TREATMENT", "fac-1", null, null);

        assertThat(register(ProgrammeVocabulary.DIABETES, "fac-1")).hasSize(1);
    }

    @Test
    void theNeverAssessedComeFirst() {
        // A register is read to decide who to recall. The people nobody has assessed are exactly who
        // that list exists to find, and a register sorted any other way buries them.
        save(TENANT, ProgrammeVocabulary.DIABETES, "ON_TREATMENT", "fac-1", "CONTROLLED", ENROLLED.plusDays(30));
        ProgrammeEnrolmentEntity unassessed =
                save(TENANT, ProgrammeVocabulary.DIABETES, "ON_TREATMENT", "fac-1", null, null);
        save(TENANT, ProgrammeVocabulary.DIABETES, "ON_TREATMENT", "fac-1", "NOT_CONTROLLED", ENROLLED.plusDays(20));

        assertThat(register(ProgrammeVocabulary.DIABETES, "fac-1").get(0).getEnrolmentId())
                .isEqualTo(unassessed.getEnrolmentId());
    }

    @Test
    void amongTheAssessedTheStalestComesFirst() {
        ProgrammeEnrolmentEntity stale =
                save(TENANT, ProgrammeVocabulary.HYPERTENSION, "ON_TREATMENT", "fac-1", "CONTROLLED", ENROLLED.plusDays(5));
        save(TENANT, ProgrammeVocabulary.HYPERTENSION, "ON_TREATMENT", "fac-1", "CONTROLLED", ENROLLED.plusDays(300));

        List<ProgrammeEnrolmentEntity> rows = register(ProgrammeVocabulary.HYPERTENSION, "fac-1");
        assertThat(rows.get(0).getEnrolmentId()).isEqualTo(stale.getEnrolmentId());
    }

    @Test
    void anExitedEntryIsOutOfTheOpenRegisterButReachableWhenAskedFor() {
        // "Who left this register and why" is a real clinical question, particularly for the people
        // who left as LOST_TO_FOLLOW_UP — so the status filter is the caller's, not hardcoded.
        ProgrammeEnrolmentEntity exited =
                save(TENANT, ProgrammeVocabulary.CHRONIC_KIDNEY_DISEASE, "EXITED", "fac-1", null, null);
        exited.setExitOn(ENROLLED.plusDays(60));
        exited.setExitReason("LOST_TO_FOLLOW_UP");
        repository.save(exited);

        assertThat(register(ProgrammeVocabulary.CHRONIC_KIDNEY_DISEASE, "fac-1")).isEmpty();
        assertThat(repository.register(TENANT, ProgrammeVocabulary.CHRONIC_KIDNEY_DISEASE, "fac-1",
                List.of("EXITED"))).hasSize(1);
    }

    @Test
    void aNullFacilityMeansTheWholeTenantRatherThanNoFacility() {
        // The difference between a national register and an accidentally empty one. An empty register
        // read as "nobody has hypertension" is the reassuring direction, which is the dangerous one.
        save(TENANT, ProgrammeVocabulary.CHRONIC_RESPIRATORY, "ON_TREATMENT", "fac-1", null, null);
        save(TENANT, ProgrammeVocabulary.CHRONIC_RESPIRATORY, "ON_TREATMENT", "fac-2", null, null);

        assertThat(register(ProgrammeVocabulary.CHRONIC_RESPIRATORY, null)).hasSize(2);
        assertThat(register(ProgrammeVocabulary.CHRONIC_RESPIRATORY, "fac-unknown")).isEmpty();
    }
}
