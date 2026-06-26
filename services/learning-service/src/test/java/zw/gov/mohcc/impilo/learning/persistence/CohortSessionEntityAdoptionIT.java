package zw.gov.mohcc.impilo.learning.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;
import zw.gov.mohcc.impilo.learning.persistence.entity.CohortMembershipEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningCohortEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CohortMembershipRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningCohortRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.ScheduledLearningSessionRepository;

/**
 * A0 — proves the newly-adopted cohort/membership/session JPA entities materialise
 * under the H2 test schema (Flyway disabled; H2 builds from entities) and round-trip
 * via their repositories, including the FK to {@code lrn_course}. This is what unblocks
 * the facilitator/venue FKs added in later waves being testable.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CohortSessionEntityAdoptionIT {

    private static final UUID TENANT = UUID.fromString("a0a0a0a0-0000-4000-8000-000000000001");

    @Autowired private CourseRepository courseRepository;
    @Autowired private LearningCohortRepository cohortRepository;
    @Autowired private CohortMembershipRepository membershipRepository;
    @Autowired private ScheduledLearningSessionRepository sessionRepository;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    @Test
    void cohortMembershipAndSessionRoundTrip() {
        CourseEntity course = new CourseEntity();
        course.setTenantId(TENANT);
        course.setCode("A0-COURSE");
        course.setTitle("A0 Course");
        course.setStatus("PUBLISHED");
        course.setLanguage("en");
        courseRepository.save(course);

        LearningCohortEntity cohort = new LearningCohortEntity();
        cohort.setTenantId(TENANT);
        cohort.setCourseId(course.getId());
        cohort.setCode("A0-COHORT");
        cohort.setTitle("A0 Cohort");
        cohort.setCreatedBy("tester");
        cohortRepository.save(cohort);

        CohortMembershipEntity member = new CohortMembershipEntity();
        member.setTenantId(TENANT);
        member.setCohortId(cohort.getId());
        member.setSubjectType("PROVIDER");
        member.setSubjectId("PRV-1");
        member.setAssignedBy("tester");
        membershipRepository.save(member);

        ScheduledLearningSessionEntity session = new ScheduledLearningSessionEntity();
        session.setTenantId(TENANT);
        session.setCourseId(course.getId());
        session.setCohortId(cohort.getId());
        session.setTitle("A0 Session");
        session.setSessionType("CLASSROOM");
        session.setStartsAt(java.time.OffsetDateTime.now());
        session.setFacilitator("Legacy Trainer Name");
        session.setLocationRef("Room 1");
        session.setCreatedBy("tester");
        sessionRepository.save(session);

        assertThat(cohortRepository.findByTenantIdAndCode(TENANT, "A0-COHORT")).isPresent();
        assertThat(membershipRepository.findByTenantIdAndCohortId(TENANT, cohort.getId())).hasSize(1);
        assertThat(sessionRepository.findByTenantIdAndCohortIdOrderByStartsAtAsc(TENANT, cohort.getId())).hasSize(1);
        assertThat(sessionRepository.findByTenantIdOrderByStartsAtDesc(TENANT, PageRequest.of(0, 10))).hasSize(1);
    }
}
