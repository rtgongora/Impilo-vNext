package zw.gov.mohcc.impilo.msikaflow.core.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.AnomalyFindingType;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.AnomalyFindingEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.AnomalyFindingRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OF-B29 — accountable finding model: dedup-idempotent recording, PII-free
 * {@code msika.flow.anomaly.detected.v1} payload contract, and the
 * reason-bound OPEN → REVIEWED → DISMISSED review lifecycle (§13.7/§21.5).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnomalyFindingServiceTest {

    /** Whitelist contract: the detected-event envelope may carry only these coded keys. */
    private static final Set<String> ALLOWED_PAYLOAD_KEYS = Set.of(
            "findingId", "findingType", "severity", "subjectType", "subjectRef",
            "tenantId", "status", "windowStart", "windowEnd", "details");

    @Mock private AnomalyFindingRepository findingRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private AnomalyFindingService service;

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AnomalyFindingService(findingRepository, outboxRepository, mapper);
        when(findingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingRepository.findFirstByTenantIdAndDedupeKey(any(), any()))
                .thenReturn(Optional.empty());
    }

    private AnomalyFindingService.FindingCandidate candidate(String dedupeKey) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("selectionId", "01SELAAAAAAAAAAAAAAAAAAAAA");
        details.put("claimTtlHours", 72);
        return new AnomalyFindingService.FindingCandidate(
                TENANT, AnomalyFindingType.CLAIM_WITHOUT_EPISODE,
                AnomalyFindingService.SEVERITY_WARN, "SELECTION", "01SELAAAAAAAAAAAAAAAAAAAAA",
                dedupeKey, OffsetDateTime.now().minusDays(3), OffsetDateTime.now(), details);
    }

    // ── recording + dedup ─────────────────────────────────────────────────

    @Test
    void record_newFinding_persistsOpenAndEmitsEvent() {
        Optional<AnomalyFindingEntity> recorded = service.record(candidate("K1"));

        assertTrue(recorded.isPresent());
        assertEquals(AnomalyFindingStatus.OPEN, recorded.get().getStatus());
        assertEquals(AnomalyFindingType.CLAIM_WITHOUT_EPISODE, recorded.get().getFindingType());
        assertNotNull(recorded.get().getFindingId());

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertEquals("ANOMALY_DETECTED", outbox.getValue().getEventType());
        assertEquals("AnomalyFinding", outbox.getValue().getAggregateType());
        assertEquals(TENANT, outbox.getValue().getTenantId());
    }

    @Test
    void record_duplicateDedupeKey_isIdempotentNoEvent() {
        when(findingRepository.findFirstByTenantIdAndDedupeKey(TENANT, "K1"))
                .thenReturn(Optional.of(new AnomalyFindingEntity()));

        Optional<AnomalyFindingEntity> recorded = service.record(candidate("K1"));

        assertTrue(recorded.isEmpty());
        verify(findingRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    // ── PII-free payload contract ─────────────────────────────────────────

    @Test
    void detectedEventPayload_isCodedAndPiiFree() throws Exception {
        service.record(candidate("K1"));

        ArgumentCaptor<EventOutboxEntity> outbox = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outbox.capture());
        JsonNode payload = mapper.readTree(outbox.getValue().getPayload());

        for (Iterator<String> keys = payload.fieldNames(); keys.hasNext(); ) {
            String key = keys.next();
            assertTrue(ALLOWED_PAYLOAD_KEYS.contains(key),
                    "unexpected top-level payload key: " + key);
        }
        // No identity/narrative fields anywhere in the payload (coded ids + counts only).
        String raw = outbox.getValue().getPayload().toLowerCase();
        for (String forbidden : new String[]{"patientname", "\"name\"", "address", "phone",
                "msisdn", "requestedby", "actorid", "diagnosis", "narrative"}) {
            assertFalse(raw.contains(forbidden), "PII-suspect key in payload: " + forbidden);
        }
    }

    // ── reason-bound review lifecycle ─────────────────────────────────────

    private AnomalyFindingEntity openFinding() {
        AnomalyFindingEntity finding = new AnomalyFindingEntity();
        finding.setFindingId("01FNDAAAAAAAAAAAAAAAAAAAAA");
        finding.setTenantId(TENANT);
        finding.setFindingType(AnomalyFindingType.CONCENTRATION_WATCH);
        finding.setSeverity(AnomalyFindingService.SEVERITY_WARN);
        finding.setSubjectType("VENDOR");
        finding.setSubjectRef("01VENAAAAAAAAAAAAAAAAAAAAA");
        finding.setDedupeKey("K");
        finding.setStatus(AnomalyFindingStatus.OPEN);
        when(findingRepository.findByFindingIdAndTenantId(finding.getFindingId(), TENANT))
                .thenReturn(Optional.of(finding));
        return finding;
    }

    @Test
    void review_open_movesToReviewedWithAccountability() {
        AnomalyFindingEntity finding = openFinding();

        AnomalyFindingEntity reviewed = service.review(
                finding.getFindingId(), TENANT, "ops-actor-1", "confirmed with vendor ops");

        assertEquals(AnomalyFindingStatus.REVIEWED, reviewed.getStatus());
        assertEquals("ops-actor-1", reviewed.getReviewedBy());
        assertEquals("confirmed with vendor ops", reviewed.getReviewReason());
        assertNotNull(reviewed.getReviewedAt());
    }

    @Test
    void review_withoutReason_isRejected() {
        AnomalyFindingEntity finding = openFinding();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.review(finding.getFindingId(), TENANT, "ops-actor-1", "  "));
        assertTrue(e.getMessage().startsWith("REASON_REQUIRED"));
    }

    @Test
    void review_withoutReviewer_isRejected() {
        AnomalyFindingEntity finding = openFinding();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.review(finding.getFindingId(), TENANT, null, "reason"));
        assertTrue(e.getMessage().startsWith("REVIEWER_REQUIRED"));
    }

    @Test
    void review_nonOpen_isRejected() {
        AnomalyFindingEntity finding = openFinding();
        finding.setStatus(AnomalyFindingStatus.REVIEWED);

        assertThrows(IllegalStateException.class,
                () -> service.review(finding.getFindingId(), TENANT, "ops-actor-1", "again"));
    }

    @Test
    void dismiss_fromOpenAndReviewed_reachesTerminal() {
        AnomalyFindingEntity finding = openFinding();

        AnomalyFindingEntity dismissed = service.dismiss(
                finding.getFindingId(), TENANT, "ops-actor-2", "known seed-data artefact");
        assertEquals(AnomalyFindingStatus.DISMISSED, dismissed.getStatus());
        assertEquals("ops-actor-2", dismissed.getReviewedBy());

        // REVIEWED → DISMISSED also allowed
        AnomalyFindingEntity second = openFinding();
        second.setFindingId("01FNDBBBBBBBBBBBBBBBBBBBBB");
        second.setStatus(AnomalyFindingStatus.REVIEWED);
        when(findingRepository.findByFindingIdAndTenantId(second.getFindingId(), TENANT))
                .thenReturn(Optional.of(second));
        assertEquals(AnomalyFindingStatus.DISMISSED,
                service.dismiss(second.getFindingId(), TENANT, "ops-actor-2", "resolved upstream")
                        .getStatus());
    }

    @Test
    void dismiss_terminal_isRejected() {
        AnomalyFindingEntity finding = openFinding();
        finding.setStatus(AnomalyFindingStatus.DISMISSED);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.dismiss(finding.getFindingId(), TENANT, "ops-actor-1", "again"));
        assertTrue(e.getMessage().startsWith("FINDING_TERMINAL"));
    }

    @Test
    void get_unknownOrWrongTenant_isNotFound() {
        when(findingRepository.findByFindingIdAndTenantId(any(), any())).thenReturn(Optional.empty());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.get("01FNDZZZZZZZZZZZZZZZZZZZZZ", TENANT));
        assertTrue(e.getMessage().startsWith("FINDING_NOT_FOUND"));
    }
}
