package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.config.ZiboProperties;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.domain.JobStatus;
import zw.gov.mohcc.impilo.zibo.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ValidationJobEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ValidationLogEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The async validation path could not work, and nothing said so.
 *
 * <p>{@code processPendingJobs} is {@code @Scheduled}. Every path beneath it reaches
 * {@code TrustContextHolder.require()}, which throws when unset — and on a scheduler thread there
 * is no inbound request, so there is no context. Every queued job therefore threw, was caught by
 * the surrounding try, and landed in {@code FAILED}. {@code zibo_validation_jobs} sitting at zero
 * rows is why nobody noticed: the feature has never been exercised, so its total failure looked
 * exactly like disuse.</p>
 *
 * <p>These are the first tests of that path.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduledValidationJobTest {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-4000-8000-00000000000a");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-4000-8000-00000000000b");
    private static final UUID NATIONAL = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private ArtifactRepository artifactRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private ValidationJobRepository validationJobRepository;
    @Mock private ValidationLogRepository validationLogRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ValidationEngine engine;

    @BeforeEach
    void setUp() {
        ZiboProperties props = new ZiboProperties();
        props.getValidation().setDefaultMode("STRICT");
        engine = new ValidationEngine(artifactRepository, assignmentRepository,
                validationJobRepository, validationLogRepository, outboxRepository, props,
                new ObjectMapper(),
                new ArtifactResolutionService(artifactRepository, new SimpleMeterRegistry(),
                        NATIONAL.toString()),
                new SimpleMeterRegistry());
        when(assignmentRepository.findByTenantIdAndScopeTypeAndScopeIdAndActiveTrue(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(artifactRepository.findCandidatesAcrossPlanes(any(), any(), any())).thenReturn(List.of());
        when(validationJobRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static ValidationJobEntity job(UUID tenant, String payload) {
        ValidationJobEntity j = new ValidationJobEntity();
        j.setId(UUID.randomUUID());
        j.setTenantId(tenant);
        j.setStatus(JobStatus.PENDING);
        j.setPayloadJson(payload);
        return j;
    }

    private static final String RESOURCE =
            "{\"resourceType\":\"Observation\",\"code\":{\"coding\":[{"
            + "\"system\":\"http://loinc.org\",\"code\":\"8480-6\"}]}}";

    @Test
    void aQueuedJobCompletesInsteadOfFailingForWantOfATrustContext() {
        ValidationJobEntity j = job(TENANT_A, RESOURCE);
        when(validationJobRepository.findByStatus(JobStatus.PENDING)).thenReturn(List.of(j));

        engine.processPendingJobs();

        ArgumentCaptor<ValidationJobEntity> cap = ArgumentCaptor.forClass(ValidationJobEntity.class);
        verify(validationJobRepository, atLeastOnce()).save(cap.capture());
        assertThat(cap.getAllValues().get(cap.getAllValues().size() - 1).getStatus())
                .describedAs("before the synthetic context, every scheduled job landed in FAILED "
                        + "because require() throws off a request thread")
                .isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void eachJobRunsUnderItsOwnTenant_notASharedOne() {
        // findByStatus(PENDING) is not tenant-scoped. A single service-wide context would evaluate
        // one tenant's payload under another's authority and resolve artifacts from the wrong plane.
        when(validationJobRepository.findByStatus(JobStatus.PENDING))
                .thenReturn(List.of(job(TENANT_A, RESOURCE), job(TENANT_B, RESOURCE)));

        engine.processPendingJobs();

        ArgumentCaptor<ValidationLogEntity> logs = ArgumentCaptor.forClass(ValidationLogEntity.class);
        verify(validationLogRepository, atLeastOnce()).save(logs.capture());
        assertThat(logs.getAllValues())
                .extracting(ValidationLogEntity::getTenantId)
                .describedAs("each job's telemetry must be attributed to the tenant that queued it")
                .contains(TENANT_A, TENANT_B);
    }

    @Test
    void theContextIsClearedBetweenJobs_soNothingLeaksOntoTheSchedulerThread() {
        when(validationJobRepository.findByStatus(JobStatus.PENDING))
                .thenReturn(List.of(job(TENANT_A, RESOURCE)));

        engine.processPendingJobs();

        assertThat(TrustContextHolder.get())
                .describedAs("a scheduler thread is pooled and reused — a context left behind would "
                        + "silently authorise the next job that forgot to set one")
                .isNull();
    }

    @Test
    void anOutboxEventIsNotWrittenWithoutATenant() {
        // zibo_event_outbox.tenant_id is NOT NULL. Saving a null would fail the insert and take the
        // surrounding transaction with it, losing the job result to protect an unroutable event.
        ValidationJobEntity orphan = job(null, RESOURCE);
        when(validationJobRepository.findByStatus(JobStatus.PENDING)).thenReturn(List.of(orphan));

        engine.processPendingJobs();

        verify(outboxRepository, never()).save(any(EventOutboxEntity.class));
    }

    @Test
    void aFailingJobDoesNotStopTheRest() {
        ValidationJobEntity bad = job(TENANT_A, "{ not json");
        ValidationJobEntity good = job(TENANT_B, RESOURCE);
        when(validationJobRepository.findByStatus(JobStatus.PENDING)).thenReturn(List.of(bad, good));

        engine.processPendingJobs();

        ArgumentCaptor<ValidationJobEntity> cap = ArgumentCaptor.forClass(ValidationJobEntity.class);
        verify(validationJobRepository, atLeastOnce()).save(cap.capture());
        assertThat(cap.getAllValues())
                .extracting(ValidationJobEntity::getStatus)
                .describedAs("one malformed payload must not strand the queue behind it")
                .contains(JobStatus.COMPLETED);
    }
}
