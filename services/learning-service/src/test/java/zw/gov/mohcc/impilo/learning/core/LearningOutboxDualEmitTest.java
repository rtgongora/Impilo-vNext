package zw.gov.mohcc.impilo.learning.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.learning.api.dto.VarapiFundoCompletionPayload;
import zw.gov.mohcc.impilo.learning.config.LearningProperties;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningCompletionEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningOutboxEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningPathEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningResourceEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.SubjectPathAssignmentEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.UserLearningProfileEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.ContextualLearningHintRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CpdLearningLinkRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.HelpdeskKnowledgeLinkRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningAuditEventRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningCompletionRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningOutboxRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningPathRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningProgressRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningResourceRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MoodleWsSnapshotRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.RoleLearningRequirementRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.SubjectPathAssignmentRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.UserLearningProfileRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.WorkflowLearningLinkRepository;

/**
 * Phase 4B — verifies that {@link LearningPlatformFacade} dual-emits the
 * legacy event-type strings <em>and</em> their v1.1-compliant equivalents
 * for every native write path tracked in
 * {@code RepoEventTypeContractTest.legacyEventTypes()}.
 *
 * <p>Pure Mockito unit tests with no Spring context, no JPA, no Kafka.
 * Captures every {@link LearningOutboxRepository#save} invocation, asserts
 * row counts, and asserts both event-type strings are present per write.
 * The legacy strings continue to be emitted byte-for-byte — Phase 4 does
 * not remove a single existing consumer surface.</p>
 *
 * <p>Coverage:</p>
 * <ul>
 *   <li>{@code recordResourceOpened} → 2 rows (legacy + v1.1 resource opened)</li>
 *   <li>{@code assignLearningPath} → 2 rows (legacy + v1.1 path assigned)</li>
 *   <li>{@code registerLearningPrerequisite} → 2 rows (legacy + v1.1 prerequisite registered)</li>
 *   <li>{@code ingestVarapiFundoCompletion} → 4 rows (2 legacy + 2 v1.1, completion + fundo sync)</li>
 *   <li>{@code upsertUserLearningProfile} → 2 rows (legacy + v1.1 fundo link established)</li>
 * </ul>
 *
 * <p>The Moodle ingest dual-emit ({@code moodle.ws.completion.ingested} +
 * {@code impilo.learning.moodle.ws.completion.ingested.v1}) is exercised by
 * the existing {@code LearningMoodleIngestFacadeTest} Spring slice; covering
 * it again here would require seeding a JPA snapshot entity ID.</p>
 */
class LearningOutboxDualEmitTest {

    private static final UUID TENANT = UUID.fromString("aaaa1111-1111-1111-1111-111111111111");

    private LearningResourceRepository resourceRepository;
    private LearningPathRepository pathRepository;
    private RoleLearningRequirementRepository roleRequirementRepository;
    private WorkflowLearningLinkRepository workflowLinkRepository;
    private HelpdeskKnowledgeLinkRepository helpdeskLinkRepository;
    private ContextualLearningHintRepository hintRepository;
    private LearningProgressRepository progressRepository;
    private LearningCompletionRepository completionRepository;
    private CpdLearningLinkRepository cpdLearningLinkRepository;
    private LearningAuditEventRepository auditRepository;
    private LearningOutboxRepository outboxRepository;
    private SubjectPathAssignmentRepository subjectPathAssignmentRepository;
    private MoodleWsSnapshotRepository moodleWsSnapshotRepository;
    private UserLearningProfileRepository userLearningProfileRepository;
    private LearningProperties learningProperties;

    private LearningPlatformFacade facade;

    @BeforeEach
    void setUp() {
        resourceRepository = mock(LearningResourceRepository.class);
        pathRepository = mock(LearningPathRepository.class);
        roleRequirementRepository = mock(RoleLearningRequirementRepository.class);
        workflowLinkRepository = mock(WorkflowLearningLinkRepository.class);
        helpdeskLinkRepository = mock(HelpdeskKnowledgeLinkRepository.class);
        hintRepository = mock(ContextualLearningHintRepository.class);
        progressRepository = mock(LearningProgressRepository.class);
        completionRepository = mock(LearningCompletionRepository.class);
        cpdLearningLinkRepository = mock(CpdLearningLinkRepository.class);
        auditRepository = mock(LearningAuditEventRepository.class);
        outboxRepository = mock(LearningOutboxRepository.class);
        subjectPathAssignmentRepository = mock(SubjectPathAssignmentRepository.class);
        moodleWsSnapshotRepository = mock(MoodleWsSnapshotRepository.class);
        userLearningProfileRepository = mock(UserLearningProfileRepository.class);
        learningProperties = mock(LearningProperties.class);
        // Phase 6E — the legacy-emission flag defaults to true; the dual-emit
        // tests rely on that default to assert that both legacy and v1.1 rows
        // are written. A real Events instance is wired up so the new
        // {@code appendOutboxPair} guard returns true.
        LearningProperties.Events events = new LearningProperties.Events();
        events.setLegacyEmissionEnabled(true);
        when(learningProperties.getEvents()).thenReturn(events);

        facade = new LearningPlatformFacade(
                resourceRepository,
                pathRepository,
                roleRequirementRepository,
                workflowLinkRepository,
                helpdeskLinkRepository,
                hintRepository,
                progressRepository,
                completionRepository,
                cpdLearningLinkRepository,
                auditRepository,
                outboxRepository,
                subjectPathAssignmentRepository,
                moodleWsSnapshotRepository,
                userLearningProfileRepository,
                learningProperties);
    }

    @Test
    @DisplayName("recordResourceOpened emits legacy 'learning.resource.opened' + v1.1 'impilo.learning.resource.opened.v1'")
    void resourceOpenedDualEmits() {
        UUID resourceId = UUID.randomUUID();

        facade.recordResourceOpened(TENANT, "actor-1", "USER", resourceId, "corr-1", Map.of("k", "v"));

        List<String> eventTypes = capturedEventTypes(2);
        assertThat(eventTypes).containsExactlyInAnyOrder(
                "learning.resource.opened",
                "impilo.learning.resource.opened.v1");
    }

    @Test
    @DisplayName("assignLearningPath emits legacy 'learning.path.assigned' + v1.1 'impilo.learning.path.assigned.v1'")
    void pathAssignedDualEmits() {
        UUID pathId = UUID.randomUUID();
        LearningPathEntity path = new LearningPathEntity();
        path.setId(pathId);
        path.setTenantId(TENANT);
        path.setPathCode("CPD-NEW-PROVIDER");
        path.setTitle("New provider induction");
        path.setPathType("ROLE_ENROLLMENT");
        when(pathRepository.findByTenantIdAndId(TENANT, pathId))
                .thenReturn(java.util.Optional.of(path));
        when(subjectPathAssignmentRepository
                .existsByTenantIdAndSubjectTypeAndSubjectIdAndPathId(TENANT, "USER", "user-7", pathId))
                .thenReturn(false);
        when(subjectPathAssignmentRepository.save(any(SubjectPathAssignmentEntity.class)))
                .thenAnswer(inv -> assignId(inv.getArgument(0)));

        facade.assignLearningPath(
                TENANT, "USER", "user-7", pathId, "actor-1", "corr-1", Map.of("note", "auto"));

        List<String> eventTypes = capturedEventTypes(2);
        assertThat(eventTypes).containsExactlyInAnyOrder(
                "learning.path.assigned",
                "impilo.learning.path.assigned.v1");
    }

    @Test
    @DisplayName("registerLearningPrerequisite emits legacy + v1.1 'prerequisite.registered'")
    void prerequisiteDualEmits() {
        UUID resourceId = UUID.randomUUID();

        facade.registerLearningPrerequisite(
                TENANT, "USER", "user-7", "ENROLLMENT_FLOW", resourceId, null, "actor-1", "corr-1",
                Map.of("note", "manual"));

        List<String> eventTypes = capturedEventTypes(2);
        assertThat(eventTypes).containsExactlyInAnyOrder(
                "learning.prerequisite.registered",
                "impilo.learning.prerequisite.registered.v1");
    }

    @Test
    @DisplayName("ingestVarapiFundoCompletion emits 4 rows: completion + fundo sync, each in legacy + v1.1")
    void fundoCompletionDualEmits() {
        when(completionRepository.existsByTenantIdAndSourceTypeAndSourceRef(TENANT, "FUNDO_WEBHOOK", "ext-1"))
                .thenReturn(false);
        when(resourceRepository.findByTenantIdAndResourceCode(any(), any()))
                .thenReturn(java.util.Optional.empty());
        when(resourceRepository.save(any(LearningResourceEntity.class)))
                .thenAnswer(inv -> {
                    LearningResourceEntity r = inv.getArgument(0);
                    if (r.getId() == null) {
                        r.setId(UUID.randomUUID());
                    }
                    return r;
                });
        when(completionRepository.save(any(LearningCompletionEntity.class)))
                .thenAnswer(inv -> assignId(inv.getArgument(0)));
        when(progressRepository
                .findByTenantIdAndSubjectTypeAndSubjectIdAndResourceId(any(), any(), any(), any()))
                .thenReturn(java.util.Optional.empty());

        VarapiFundoCompletionPayload payload = new VarapiFundoCompletionPayload(
                TENANT,
                "providerPub-9",
                1L,
                "course-501",
                "Course 501",
                Instant.parse("2026-05-01T00:00:00Z"),
                1,
                "ext-1",
                77L);

        facade.ingestVarapiFundoCompletion(payload);

        List<String> eventTypes = capturedEventTypes(4);
        assertThat(eventTypes).containsExactlyInAnyOrder(
                "learning.completion.recorded",
                "impilo.learning.completion.recorded.v1",
                "fundo.sync.completed",
                "impilo.learning.fundo.sync.completed.v1");
    }

    @Test
    @DisplayName("upsertUserLearningProfile emits legacy 'fundo.link.established' + v1.1 'impilo.learning.fundo.link.established.v1'")
    void fundoLinkDualEmits() {
        when(userLearningProfileRepository
                .findByTenantIdAndSubjectTypeAndSubjectId(TENANT, "USER", "user-7"))
                .thenReturn(java.util.Optional.empty());
        when(userLearningProfileRepository.save(any(UserLearningProfileEntity.class)))
                .thenAnswer(inv -> {
                    UserLearningProfileEntity e = inv.getArgument(0);
                    if (e.getId() == null) {
                        e.setId(UUID.randomUUID());
                    }
                    return e;
                });

        facade.upsertUserLearningProfile(TENANT, "USER", "user-7", true, "fundo-uid-3", false, null);

        List<String> eventTypes = capturedEventTypes(2);
        assertThat(eventTypes).containsExactlyInAnyOrder(
                "fundo.link.established",
                "impilo.learning.fundo.link.established.v1");
    }

    @Test
    @DisplayName("Every v1.1 emission carries the same payload JSON as the legacy emission for the same write")
    void dualEmitPayloadsAreIdentical() throws Exception {
        UUID resourceId = UUID.randomUUID();
        facade.recordResourceOpened(
                TENANT, "actor-1", "USER", resourceId, "corr-1", Map.of("k", "v"));

        List<LearningOutboxEntity> rows = capturedRows(2);
        ObjectMapper m = new ObjectMapper();
        ObjectNode a = (ObjectNode) m.readTree(rows.get(0).getPayloadJson());
        ObjectNode b = (ObjectNode) m.readTree(rows.get(1).getPayloadJson());
        assertThat(a).isEqualTo(b);
        assertThat(rows.get(0).getAggregateId()).isEqualTo(rows.get(1).getAggregateId());
        assertThat(rows.get(0).getAggregateType()).isEqualTo(rows.get(1).getAggregateType());
    }

    // ── helpers ────────────────────────────────────────────────────────

    private List<String> capturedEventTypes(int expectedCount) {
        return capturedRows(expectedCount).stream()
                .map(LearningOutboxEntity::getEventType)
                .toList();
    }

    private List<LearningOutboxEntity> capturedRows(int expectedCount) {
        ArgumentCaptor<LearningOutboxEntity> captor = ArgumentCaptor.forClass(LearningOutboxEntity.class);
        verify(outboxRepository, times(expectedCount)).save(captor.capture());
        return captor.getAllValues();
    }

    /**
     * Mimic the entities' {@code @PrePersist} hook (which Mockito doesn't
     * fire) by assigning a random UUID via {@code setId(UUID)} when absent.
     * Works for any learning entity whose {@code setId(UUID)} method follows
     * the platform convention.
     */
    @SuppressWarnings("unchecked")
    private static <E> E assignId(E entity) {
        try {
            java.lang.reflect.Method getId = entity.getClass().getMethod("getId");
            if (getId.invoke(entity) == null) {
                entity.getClass().getMethod("setId", UUID.class).invoke(entity, UUID.randomUUID());
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Test entity missing getId/setId(UUID)", e);
        }
        return entity;
    }
}
