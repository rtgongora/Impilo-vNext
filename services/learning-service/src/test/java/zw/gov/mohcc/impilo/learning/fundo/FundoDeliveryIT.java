package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningCohortEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.ScheduledLearningSessionEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningCohortRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.ScheduledLearningSessionRepository;

/** A1 — facilitator/venue/cohort-facilitator/session-delivery end-to-end against H2. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoDeliveryIT {

    private static final UUID TENANT = UUID.fromString("a1a1a1a1-0000-4000-8000-000000000001");

    @Autowired private FundoDeliveryService delivery;
    @Autowired private CourseRepository courseRepository;
    @Autowired private LearningCohortRepository cohortRepository;
    @Autowired private ScheduledLearningSessionRepository sessionRepository;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    @SuppressWarnings("unchecked")
    @Test
    void deliveryAdministrationRoundTrip() {
        CourseEntity course = new CourseEntity();
        course.setTenantId(TENANT);
        course.setCode("A1-COURSE");
        course.setTitle("A1 Course");
        course.setStatus("PUBLISHED");
        course.setLanguage("en");
        courseRepository.save(course);

        LearningCohortEntity cohort = new LearningCohortEntity();
        cohort.setTenantId(TENANT);
        cohort.setCourseId(course.getId());
        cohort.setCode("A1-COHORT");
        cohort.setTitle("A1 Cohort");
        cohort.setCreatedBy("tester");
        cohortRepository.save(cohort);

        // facilitator + venue
        Map<String, Object> fac = delivery.createFacilitator(TENANT, "tester",
                Map.of("displayName", "Dr Moyo", "facilitatorKind", "TRAINER", "cadre", "DOCTOR"));
        String facilitatorId = (String) fac.get("id");
        Map<String, Object> venue = delivery.createVenue(TENANT, "tester",
                Map.of("name", "Harare Training Centre", "venueKind", "PHYSICAL", "capacity", 40));
        String venueId = (String) venue.get("id");

        assertThat(((List<?>) delivery.listFacilitators(TENANT, null, 50).get("items"))).hasSize(1);
        assertThat(((List<?>) delivery.listFacilitators(TENANT, "TEACHER", 50).get("items"))).isEmpty();
        assertThat(((List<?>) delivery.listVenues(TENANT, 50).get("items"))).hasSize(1);

        // cohort ↔ facilitator
        delivery.assignCohortFacilitator(TENANT, cohort.getId().toString(), "tester",
                Map.of("facilitatorId", facilitatorId, "roleInCohort", "LEAD"));
        Map<String, Object> links = delivery.listCohortFacilitators(TENANT, cohort.getId().toString());
        assertThat(((List<?>) links.get("items"))).hasSize(1);

        // session with legacy free-text, then structured delivery assignment (read prefers FK)
        ScheduledLearningSessionEntity session = new ScheduledLearningSessionEntity();
        session.setTenantId(TENANT);
        session.setCourseId(course.getId());
        session.setCohortId(cohort.getId());
        session.setTitle("Intro session");
        session.setSessionType("CLASSROOM");
        session.setStartsAt(OffsetDateTime.now());
        session.setFacilitator("Legacy text trainer");
        session.setLocationRef("Legacy room text");
        session.setCreatedBy("tester");
        sessionRepository.save(session);

        Map<String, Object> assigned = delivery.assignSessionDelivery(TENANT, session.getId().toString(),
                Map.of("facilitatorId", facilitatorId, "venueId", venueId));
        assertThat(assigned.get("facilitatorId")).isEqualTo(facilitatorId);
        assertThat(assigned.get("venueId")).isEqualTo(venueId);
        // read-preference: resolves the structured names, not the legacy free-text
        assertThat(assigned.get("facilitatorDisplay")).isEqualTo("Dr Moyo");
        assertThat(assigned.get("venueDisplay")).isEqualTo("Harare Training Centre");

        // status update
        Map<String, Object> updated = delivery.updateFacilitatorStatus(TENANT, facilitatorId, "INACTIVE");
        assertThat(updated.get("status")).isEqualTo("INACTIVE");
    }
}
