package zw.gov.mohcc.impilo.participation.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.participation.events.ParticipationEventEmitter;
import zw.gov.mohcc.impilo.participation.persistence.entity.TestingCohortEntity;
import zw.gov.mohcc.impilo.participation.persistence.entity.TestingEnrollmentEntity;
import zw.gov.mohcc.impilo.participation.persistence.repository.TestingCohortRepository;
import zw.gov.mohcc.impilo.participation.persistence.repository.TestingEnrollmentRepository;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Testing / early-access cohorts: lists open cohorts and enrols a citizen. Enrolment accepts
 * either an authenticated contributor ref or an opaque volunteered contact for follow-up.
 */
@Service
public class TestingCohortService {

    private static final int MAX_CONTACT = 255;

    private final TestingCohortRepository cohortRepository;
    private final TestingEnrollmentRepository enrollmentRepository;
    private final ParticipationEventEmitter emitter;

    public TestingCohortService(TestingCohortRepository cohortRepository,
                                TestingEnrollmentRepository enrollmentRepository,
                                ParticipationEventEmitter emitter) {
        this.cohortRepository = cohortRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.emitter = emitter;
    }

    @Transactional(readOnly = true)
    public List<TestingCohortEntity> openCohorts(UUID tenantId) {
        return cohortRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<TestingCohortEntity> allCohorts(UUID tenantId) {
        return cohortRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional
    public TestingEnrollmentEntity enroll(UUID tenantId, UUID cohortId, String contributorRef, String contact) {
        TestingCohortEntity cohort = cohortRepository.findByIdAndTenantId(cohortId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Testing cohort not found: " + cohortId));
        if (Boolean.FALSE.equals(cohort.getActive())) {
            throw new IllegalStateException("This testing cohort is not open for sign-up.");
        }
        boolean hasContributor = contributorRef != null && !contributorRef.isBlank();
        boolean hasContact = contact != null && !contact.isBlank();
        if (!hasContributor && !hasContact) {
            throw new IllegalArgumentException("Sign in or provide a contact so we can reach you about testing.");
        }
        if (hasContact && contact.length() > MAX_CONTACT) {
            throw new IllegalArgumentException("Contact is too long.");
        }
        // An authenticated citizen enrols in a given cohort at most once.
        if (hasContributor && enrollmentRepository.existsByCohortIdAndContributorRef(cohortId, contributorRef)) {
            throw new IllegalStateException("You are already enrolled in this testing cohort.");
        }

        TestingEnrollmentEntity e = new TestingEnrollmentEntity();
        e.setCohortId(cohortId);
        e.setTenantId(tenantId);
        e.setContributorRef(hasContributor ? contributorRef : null);
        e.setContact(hasContact ? contact.trim() : null);
        e.setSegment(cohort.getSegment());
        enrollmentRepository.save(e);

        emitter.emit("TESTING_ENROLLMENT", e.getId().toString(), "participation.testing.enrolled",
                "TESTING_COHORT", cohortId.toString(),
                Map.of("cohortId", cohortId.toString(), "segment", cohort.getSegment()), tenantId);
        return e;
    }
}
