package zw.gov.mohcc.impilo.learning.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.learning.config.LearningProperties;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningOutboxEntity;
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
 * Phase 6E — controlled retirement of the seven legacy event-type strings.
 *
 * <p>Verifies that the {@code learning.events.legacy-emission.enabled}
 * flag controls whether the legacy event-type row is written alongside the
 * v1.1-compliant row. With the flag enabled (the default), the existing
 * Phase 4B dual-emit cycle is unchanged. With the flag disabled, only the
 * v1.1 row is written — proving the migration cycle can actually finish
 * without renaming a single event type or deleting any constant.</p>
 *
 * <p>Pure Mockito unit tests; no Spring context, no JPA, no Kafka. Uses
 * {@code recordResourceOpened} as a single representative write path
 * because {@link LearningPlatformFacade#appendOutboxPair} is shared across
 * every dual-emit call site.</p>
 */
class LearningLegacyEmissionRetirementTest {

    private static final UUID TENANT = UUID.fromString("ffffffff-1111-2222-3333-444444444444");

    private LearningOutboxRepository outboxRepository;
    private LearningProperties learningProperties;
    private LearningProperties.Events events;
    private LearningPlatformFacade facade;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(LearningOutboxRepository.class);
        learningProperties = mock(LearningProperties.class);
        events = new LearningProperties.Events();
        when(learningProperties.getEvents()).thenReturn(events);

        facade = new LearningPlatformFacade(
                mock(LearningResourceRepository.class),
                mock(LearningPathRepository.class),
                mock(RoleLearningRequirementRepository.class),
                mock(WorkflowLearningLinkRepository.class),
                mock(HelpdeskKnowledgeLinkRepository.class),
                mock(ContextualLearningHintRepository.class),
                mock(LearningProgressRepository.class),
                mock(LearningCompletionRepository.class),
                mock(CpdLearningLinkRepository.class),
                mock(LearningAuditEventRepository.class),
                outboxRepository,
                mock(SubjectPathAssignmentRepository.class),
                mock(MoodleWsSnapshotRepository.class),
                mock(UserLearningProfileRepository.class),
                learningProperties);
    }

    @Test
    @DisplayName("Default mode (flag=true): both legacy and v1.1 rows written")
    void defaultDualEmits() {
        events.setLegacyEmissionEnabled(true);

        facade.recordResourceOpened(TENANT, "actor-1", "USER", UUID.randomUUID(), "corr-1", Map.of("k", "v"));

        List<String> types = capturedEventTypes(2);
        assertThat(types).containsExactlyInAnyOrder(
                "learning.resource.opened",
                "impilo.learning.resource.opened.v1");
    }

    @Test
    @DisplayName("Retired mode (flag=false): only the v1.1 row is written")
    void retiredModeOnlyV11() {
        events.setLegacyEmissionEnabled(false);

        facade.recordResourceOpened(TENANT, "actor-1", "USER", UUID.randomUUID(), "corr-1", Map.of("k", "v"));

        List<String> types = capturedEventTypes(1);
        assertThat(types).containsExactly("impilo.learning.resource.opened.v1");
    }

    @Test
    @DisplayName("Flag flip is honoured across separate invocations on the same facade instance")
    void flagFlipHonoured() {
        events.setLegacyEmissionEnabled(false);
        facade.recordResourceOpened(TENANT, "actor-1", "USER", UUID.randomUUID(), "corr-1", Map.of());

        events.setLegacyEmissionEnabled(true);
        facade.recordResourceOpened(TENANT, "actor-2", "USER", UUID.randomUUID(), "corr-2", Map.of());

        // 1 v1.1 row from the first call + 2 rows (legacy + v1.1) from the second.
        List<String> types = capturedEventTypes(3);
        assertThat(types).filteredOn(t -> t.equals("learning.resource.opened")).hasSize(1);
        assertThat(types).filteredOn(t -> t.equals("impilo.learning.resource.opened.v1")).hasSize(2);
    }

    private List<String> capturedEventTypes(int expectedCount) {
        ArgumentCaptor<LearningOutboxEntity> captor = ArgumentCaptor.forClass(LearningOutboxEntity.class);
        verify(outboxRepository, times(expectedCount)).save(captor.capture());
        return captor.getAllValues().stream().map(LearningOutboxEntity::getEventType).toList();
    }
}
