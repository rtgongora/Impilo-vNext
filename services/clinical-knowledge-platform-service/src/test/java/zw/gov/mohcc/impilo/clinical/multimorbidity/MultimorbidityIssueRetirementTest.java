package zw.gov.mohcc.impilo.clinical.multimorbidity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.clinical.events.ClinicalKafkaTopics;
import zw.gov.mohcc.impilo.clinical.events.ClinicalOutboxWriter;
import zw.gov.mohcc.impilo.clinical.persistence.entity.MultimorbidityDetectionSnapshotEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.MultimorbidityDetectionSnapshotRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DetectedIssue retirement (brief.md §19 / handover Wave 2).
 *
 * <p>The failure this pack is written against: stop the duplicate anticoagulant, the engine goes
 * quiet, no event fires, and the archived FHIR DetectedIssue stays FINAL forever. Guessing
 * clearance from silence is forbidden; so is treating UNDETERMINED as clearance.</p>
 */
@ExtendWith(MockitoExtension.class)
class MultimorbidityIssueRetirementTest {

    @Mock ClinicalOutboxWriter outboxWriter;
    @Mock MultimorbidityDetectionSnapshotRepository snapshotRepository;

    MultimorbidityShrPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new MultimorbidityShrPublisher(outboxWriter, snapshotRepository);
    }

    private static MultimorbidityView.Finding anticoag() {
        return new MultimorbidityView.Finding(
                "MM_DUPLICATE_ANTICOAGULANT", "HIGH", "m", "e", "a");
    }

    private static MultimorbidityView detectedView() {
        return new MultimorbidityView(
                List.of(),
                List.of(MultimorbidityView.Detection.detected(
                        "duplicate_medicines", "Duplicate medicines", List.of(anticoag()))),
                "2026.07", "APPROVED", null);
    }

    private static MultimorbidityView clearView() {
        return new MultimorbidityView(
                List.of(),
                List.of(MultimorbidityView.Detection.clear(
                        "duplicate_medicines", "Duplicate medicines")),
                "2026.07", "APPROVED", null);
    }

    private static MultimorbidityView undeterminedView() {
        return new MultimorbidityView(
                List.of(),
                List.of(MultimorbidityView.Detection.undetermined(
                        "duplicate_medicines", "Duplicate medicines",
                        "The medicine list could not be obtained.")),
                "2026.07", "APPROVED", "Medicine list unavailable.");
    }

    private static MultimorbidityDetectionSnapshotEntity openRow() {
        MultimorbidityDetectionSnapshotEntity row = new MultimorbidityDetectionSnapshotEntity();
        row.setTenantId("tenant-1");
        row.setSubjectCpid("CPID-9");
        row.setCode("MM_DUPLICATE_ANTICOAGULANT");
        row.setDetectionKey("duplicate_medicines");
        row.setSeverity("HIGH");
        row.setContentVersion("2026.07");
        row.setDetectedAt(Instant.parse("2026-07-01T10:00:00Z"));
        row.setStatus(MultimorbidityDetectionSnapshotEntity.STATUS_OPEN);
        return row;
    }

    @Test
    void aDetectedFindingOpensASnapshotRow() {
        when(snapshotRepository.findByTenantIdAndSubjectCpidAndCode(
                "tenant-1", "CPID-9", "MM_DUPLICATE_ANTICOAGULANT"))
                .thenReturn(Optional.empty());
        when(snapshotRepository.findByTenantIdAndSubjectCpidAndStatus(
                "tenant-1", "CPID-9", MultimorbidityDetectionSnapshotEntity.STATUS_OPEN))
                .thenReturn(List.of());

        int written = publisher.publish(detectedView(), "CPID-9", "tenant-1");

        assertThat(written).isEqualTo(1);
        verify(outboxWriter).enqueue(eq(MultimorbidityShrPublisher.EVENT_TYPE), eq("tenant-1"),
                eq("MultimorbidityIssue"), eq("CPID-9|MM_DUPLICATE_ANTICOAGULANT"), any());
        ArgumentCaptor<MultimorbidityDetectionSnapshotEntity> saved =
                ArgumentCaptor.forClass(MultimorbidityDetectionSnapshotEntity.class);
        verify(snapshotRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus())
                .isEqualTo(MultimorbidityDetectionSnapshotEntity.STATUS_OPEN);
        assertThat(saved.getValue().getCode()).isEqualTo("MM_DUPLICATE_ANTICOAGULANT");
    }

    @Test
    void detectedToNotDetectedEmitsResolvedAndRetiresTheSnapshot() {
        MultimorbidityDetectionSnapshotEntity open = openRow();
        when(snapshotRepository.findByTenantIdAndSubjectCpidAndStatus(
                "tenant-1", "CPID-9", MultimorbidityDetectionSnapshotEntity.STATUS_OPEN))
                .thenReturn(List.of(open));

        int written = publisher.publish(clearView(), "CPID-9", "tenant-1");

        assertThat(written).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxWriter).enqueue(eq(MultimorbidityShrPublisher.RESOLVED_EVENT_TYPE), eq("tenant-1"),
                eq("MultimorbidityIssue"), eq("CPID-9|MM_DUPLICATE_ANTICOAGULANT"), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("code", "MM_DUPLICATE_ANTICOAGULANT")
                .containsEntry("detectionKey", "duplicate_medicines")
                .containsKey("resolvedAt")
                .doesNotContainKeys("message", "explanation", "requiredAction");
        assertThat(open.getStatus()).isEqualTo(MultimorbidityDetectionSnapshotEntity.STATUS_RETIRED);
        assertThat(open.getResolvedAt()).isNotNull();
        verify(snapshotRepository).save(open);
    }

    @Test
    void detectedToUndeterminedDoesNotRetire() {
        when(snapshotRepository.findByTenantIdAndSubjectCpidAndStatus(
                "tenant-1", "CPID-9", MultimorbidityDetectionSnapshotEntity.STATUS_OPEN))
                .thenReturn(List.of(openRow()));

        int written = publisher.publish(undeterminedView(), "CPID-9", "tenant-1");

        verify(outboxWriter, never()).enqueue(eq(MultimorbidityShrPublisher.RESOLVED_EVENT_TYPE),
                any(), any(), any(), any());
        assertThat(written).isEqualTo(0);
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void assessWithoutSubjectCpidNeitherDetectsNorRetires() {
        int written = publisher.publish(clearView(), null, "tenant-1");

        assertThat(written).isEqualTo(0);
        verifyNoInteractions(outboxWriter, snapshotRepository);
    }

    @Test
    void silenceHasNoPublisherCallSoNothingIsRetired() {
        // Documented by absence: retirement only runs inside publish(). No assess → no publish →
        // no RESOLVED. This assertion keeps the invariant visible in the suite.
        verifyNoInteractions(outboxWriter, snapshotRepository);
        assertThat(MultimorbidityShrPublisher.wasAnswered(null)).isFalse();
        assertThat(MultimorbidityShrPublisher.wasAnswered(
                MultimorbidityView.Detection.undetermined("k", "t", "r"))).isFalse();
        assertThat(MultimorbidityShrPublisher.wasAnswered(
                MultimorbidityView.Detection.clear("k", "t"))).isTrue();
    }

    @Test
    void resolvedEventTypeIsRoutedToItsOwnTopicAndNotTheCatchAll() {
        assertThat(ClinicalKafkaTopics.topicForEventType(MultimorbidityShrPublisher.RESOLVED_EVENT_TYPE))
                .isEqualTo("impilo.clinical.multimorbidity.issue.resolved")
                .isNotEqualTo("impilo.clinical.events");
    }

    @Test
    void resolvedPayloadCarriesNoEngineProse() {
        Map<String, Object> payload = MultimorbidityShrPublisher.resolvedPayload(
                "CPID-9", "tenant-1", openRow(), "2026.07", "2026-07-30T12:00:00Z");
        assertThat(payload.keySet()).containsExactlyInAnyOrder(
                "subjectCpid", "tenantId", "detectionKey", "code", "contentVersion", "resolvedAt");
    }
}
