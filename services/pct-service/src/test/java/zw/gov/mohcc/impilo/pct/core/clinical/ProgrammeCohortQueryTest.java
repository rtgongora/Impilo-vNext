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
 * Executes the cohort-count JPQL against a real persistence context.
 *
 * <p><strong>This test could not exist until now.</strong> The pct Spring context was unbootable
 * under the H2 test profile — an {@code UPDATE … SET e.identityResolvedAt = CURRENT_TIMESTAMP} in
 * {@code EmergencyEpisodeRepository} failed Hibernate's semantic check on H2 — and the failure went
 * unnoticed because all six {@code @SpringBootTest} classes in this service are named {@code *IT}
 * and surefire does not run them. The context could not boot, and nothing was trying to boot it; the
 * two facts protected each other. Fixed on canonical by binding {@code :now}.</p>
 *
 * <p>Named {@code *Test} deliberately, so it actually runs. It closes a gap this pack had recorded
 * honestly rather than papered over: the cohort query previously shipped verified by review only.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
// Each method rolls back. Without this the saves persist across methods and the counts accumulate —
// which is how the first run failed (4 where 2 was expected). Worth noting the failure was in the
// TEST, not the query: the tenant filter had correctly excluded the other tenant's rows throughout.
@Transactional
class ProgrammeCohortQueryTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Autowired
    private ProgrammeEnrolmentRepository repository;

    private ProgrammeEnrolmentEntity enrolment(UUID tenant, String programme, String status, String facility) {
        ProgrammeEnrolmentEntity e = new ProgrammeEnrolmentEntity();
        e.setEnrolmentId(UUID.randomUUID());
        e.setTenantId(tenant);
        e.setSubjectCpid("cpid-" + UUID.randomUUID());
        e.setProgramme(programme);
        e.setStatus(status);
        e.setAnchorProblemId(UUID.randomUUID());
        e.setEnrolledOn(LocalDate.of(2026, 1, 1));
        e.setManagingFacilityId(facility);
        e.setCreatedBy("test");
        return e;
    }

    @Test
    void theQueryRunsAndGroupsByProgrammeAndStatus() {
        // That this executes at all is half the point: the JPQL is now proven to parse and run,
        // not merely to compile.
        repository.save(enrolment(TENANT, "HIV_CARE", "ON_TREATMENT", "fac-1"));
        repository.save(enrolment(TENANT, "HIV_CARE", "ON_TREATMENT", "fac-1"));
        repository.save(enrolment(TENANT, "HIV_CARE", "EXITED", "fac-1"));
        repository.save(enrolment(TENANT, "TB_TREATMENT", "ON_TREATMENT", "fac-2"));

        List<Object[]> rows = repository.cohortCounts(TENANT, null);

        assertThat(countOf(rows, "HIV_CARE", "ON_TREATMENT")).isEqualTo(2);
        assertThat(countOf(rows, "HIV_CARE", "EXITED")).isEqualTo(1);
        assertThat(countOf(rows, "TB_TREATMENT", "ON_TREATMENT")).isEqualTo(1);
    }

    @Test
    void anotherTenantsCohortNeverLeaksIntoThisOne() {
        repository.save(enrolment(TENANT, "HIV_CARE", "ON_TREATMENT", "fac-1"));
        repository.save(enrolment(OTHER_TENANT, "HIV_CARE", "ON_TREATMENT", "fac-1"));
        repository.save(enrolment(OTHER_TENANT, "TB_TREATMENT", "ON_TREATMENT", "fac-1"));

        assertThat(totalOf(repository.cohortCounts(TENANT, null))).isEqualTo(1);
    }

    @Test
    void aNullFacilityMeansTheWholeTenantRatherThanNoFacility() {
        // The null-guard in the query is the difference between a national figure and an accidental
        // empty one — and an empty cohort read as "nobody is enrolled" is the reassuring direction.
        repository.save(enrolment(TENANT, "HIV_CARE", "ON_TREATMENT", "fac-1"));
        repository.save(enrolment(TENANT, "HIV_CARE", "ON_TREATMENT", "fac-2"));

        assertThat(totalOf(repository.cohortCounts(TENANT, null))).isEqualTo(2);
        assertThat(totalOf(repository.cohortCounts(TENANT, "fac-1"))).isEqualTo(1);
        assertThat(totalOf(repository.cohortCounts(TENANT, "fac-unknown"))).isZero();
    }

    private static long countOf(List<Object[]> rows, String programme, String status) {
        return rows.stream()
                .filter(r -> programme.equals(r[0]) && status.equals(r[1]))
                .mapToLong(r -> ((Number) r[2]).longValue()).sum();
    }

    private static long totalOf(List<Object[]> rows) {
        return rows.stream().mapToLong(r -> ((Number) r[2]).longValue()).sum();
    }
}
