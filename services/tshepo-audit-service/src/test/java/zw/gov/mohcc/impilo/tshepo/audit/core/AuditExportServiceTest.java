package zw.gov.mohcc.impilo.tshepo.audit.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditExportRequest;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditExportResponse;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.ExportVerificationResponse;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.SignRequest;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.SignResponse;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditEventEntity;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditExportEntity;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.repository.AuditEventRepository;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.repository.AuditExportRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditExportService}.
 *
 * Validates export bundle creation, aggregate hash computation, signature
 * acquisition from tshepo-keys-service, export verification, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class AuditExportServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID EXPORT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final String KEYS_BASE_URL = "http://tshepo-keys:8087";
    private static final String SIGN_ENDPOINT = "/v1/sign";
    private static final String MOCK_SIGNATURE = "ed25519sig_abcdef1234567890abcdef1234567890";
    private static final String MOCK_KEY_ID = "key-2025-001";

    @Mock
    private AuditEventRepository eventRepository;

    @Mock
    private AuditExportRepository exportRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditExportService auditExportService;

    @Captor
    private ArgumentCaptor<AuditExportEntity> exportCaptor;

    @Captor
    private ArgumentCaptor<SignRequest> signRequestCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(auditExportService, "keysServiceBaseUrl", KEYS_BASE_URL);
        ReflectionTestUtils.setField(auditExportService, "signEndpoint", SIGN_ENDPOINT);
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    private AuditEventEntity buildEventWithHash(UUID tenantId, long seq, String entryHash) {
        AuditEventEntity event = new AuditEventEntity();
        event.setId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setEventType("AUTH_DECISION");
        event.setActorId("user-" + seq);
        event.setActorType("PRACTITIONER");
        event.setAction("READ");
        event.setOutcome("SUCCESS");
        event.setPreviousHash("prev-" + seq);
        event.setEntryHash(entryHash);
        event.setSequenceNumber(seq);
        event.setCreatedAt(Instant.parse("2025-06-15T10:00:00Z").plusSeconds(seq));
        event.setCorrelationId(UUID.randomUUID());
        return event;
    }

    /**
     * Compute the expected aggregate bundle hash over a list of entry hashes,
     * replicating the logic in AuditExportService.computeBundleHash.
     */
    private String computeExpectedBundleHash(List<String> entryHashes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String hash : entryHashes) {
                digest.update(hash.getBytes(StandardCharsets.UTF_8));
            }
            byte[] hashBytes = digest.digest();
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private AuditExportRequest buildExportRequest(long from, long to) {
        return new AuditExportRequest(TENANT_ID, from, to, "admin@example.com");
    }

    private void stubSigningService() {
        SignResponse signResponse = new SignResponse(MOCK_SIGNATURE, MOCK_KEY_ID, "Ed25519");
        when(restTemplate.postForEntity(
                eq(KEYS_BASE_URL + SIGN_ENDPOINT),
                any(SignRequest.class),
                eq(SignResponse.class)))
                .thenReturn(ResponseEntity.ok(signResponse));
    }

    private AuditExportEntity buildExportEntity(String bundleHash, long from, long to, int entryCount) {
        AuditExportEntity export = new AuditExportEntity();
        export.setId(EXPORT_ID);
        export.setTenantId(TENANT_ID);
        export.setRequestedBy("admin@example.com");
        export.setFromSequence(from);
        export.setToSequence(to);
        export.setEntryCount(entryCount);
        export.setBundleHash(bundleHash);
        export.setSignature(MOCK_SIGNATURE);
        export.setStatus("COMPLETED");
        export.setCreatedAt(Instant.parse("2025-06-15T12:00:00Z"));
        return export;
    }

    // -----------------------------------------------------------------------
    // createExport tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Export bundle contains all events in the requested sequence range")
    void exportBundle_containsAllEventsInRange() {
        // Given: three events in the range [1, 3]
        String hash1 = "1111111111111111111111111111111111111111111111111111111111111111";
        String hash2 = "2222222222222222222222222222222222222222222222222222222222222222";
        String hash3 = "3333333333333333333333333333333333333333333333333333333333333333";

        AuditEventEntity event1 = buildEventWithHash(TENANT_ID, 1L, hash1);
        AuditEventEntity event2 = buildEventWithHash(TENANT_ID, 2L, hash2);
        AuditEventEntity event3 = buildEventWithHash(TENANT_ID, 3L, hash3);

        when(eventRepository.findByTenantIdAndSequenceNumberBetween(TENANT_ID, 1L, 3L))
                .thenReturn(List.of(event1, event2, event3));

        stubSigningService();

        when(exportRepository.save(any(AuditExportEntity.class)))
                .thenAnswer(invocation -> {
                    AuditExportEntity entity = invocation.getArgument(0);
                    entity.setId(EXPORT_ID);
                    return entity;
                });

        AuditExportRequest request = buildExportRequest(1L, 3L);

        // When
        AuditExportResponse result = auditExportService.createExport(request);

        // Then: response contains correct metadata
        assertThat(result.id()).isEqualTo(EXPORT_ID);
        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.fromSequence()).isEqualTo(1L);
        assertThat(result.toSequence()).isEqualTo(3L);
        assertThat(result.entryCount()).isEqualTo(3);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.requestedBy()).isEqualTo("admin@example.com");

        // Then: bundle hash is the aggregate SHA-256 of all entry hashes
        String expectedBundleHash = computeExpectedBundleHash(List.of(hash1, hash2, hash3));
        assertThat(result.bundleHash()).isEqualTo(expectedBundleHash);

        // Then: the export entity was persisted with correct values
        verify(exportRepository).save(exportCaptor.capture());
        AuditExportEntity saved = exportCaptor.getValue();
        assertThat(saved.getEntryCount()).isEqualTo(3);
        assertThat(saved.getBundleHash()).isEqualTo(expectedBundleHash);
        assertThat(saved.getFromSequence()).isEqualTo(1L);
        assertThat(saved.getToSequence()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Export bundle includes Ed25519 signature from keys service")
    void exportBundle_includesSignature() {
        // Given: one event in range [1, 1]
        String hash = "aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111aaaa1111";
        AuditEventEntity event = buildEventWithHash(TENANT_ID, 1L, hash);

        when(eventRepository.findByTenantIdAndSequenceNumberBetween(TENANT_ID, 1L, 1L))
                .thenReturn(List.of(event));

        stubSigningService();

        when(exportRepository.save(any(AuditExportEntity.class)))
                .thenAnswer(invocation -> {
                    AuditExportEntity entity = invocation.getArgument(0);
                    entity.setId(EXPORT_ID);
                    return entity;
                });

        AuditExportRequest request = buildExportRequest(1L, 1L);

        // When
        AuditExportResponse result = auditExportService.createExport(request);

        // Then: signature is present in the response
        assertThat(result.signature()).isEqualTo(MOCK_SIGNATURE);

        // Then: the signing service was called with Ed25519 algorithm
        verify(restTemplate).postForEntity(
                eq(KEYS_BASE_URL + SIGN_ENDPOINT),
                signRequestCaptor.capture(),
                eq(SignResponse.class));

        SignRequest capturedSignRequest = signRequestCaptor.getValue();
        String expectedBundleHash = computeExpectedBundleHash(List.of(hash));
        assertThat(capturedSignRequest.payload()).isEqualTo(expectedBundleHash);
        assertThat(capturedSignRequest.algorithm()).isEqualTo("Ed25519");

        // Then: the persisted export contains the signature
        verify(exportRepository).save(exportCaptor.capture());
        assertThat(exportCaptor.getValue().getSignature()).isEqualTo(MOCK_SIGNATURE);
    }

    @Test
    @DisplayName("Export with single event in range creates valid bundle")
    void exportBundle_singleEvent_createsValidBundle() {
        String hash = "5555555555555555555555555555555555555555555555555555555555555555";
        AuditEventEntity event = buildEventWithHash(TENANT_ID, 10L, hash);

        when(eventRepository.findByTenantIdAndSequenceNumberBetween(TENANT_ID, 10L, 10L))
                .thenReturn(List.of(event));

        stubSigningService();

        when(exportRepository.save(any(AuditExportEntity.class)))
                .thenAnswer(invocation -> {
                    AuditExportEntity entity = invocation.getArgument(0);
                    entity.setId(EXPORT_ID);
                    return entity;
                });

        AuditExportRequest request = buildExportRequest(10L, 10L);

        AuditExportResponse result = auditExportService.createExport(request);

        assertThat(result.entryCount()).isEqualTo(1);
        assertThat(result.bundleHash()).isEqualTo(computeExpectedBundleHash(List.of(hash)));
        assertThat(result.signature()).isEqualTo(MOCK_SIGNATURE);
    }

    // -----------------------------------------------------------------------
    // createExport error handling
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Export throws IllegalArgumentException when fromSequence > toSequence")
    void exportBundle_invalidRange_throwsIllegalArgument() {
        AuditExportRequest request = buildExportRequest(5L, 3L);

        assertThatThrownBy(() -> auditExportService.createExport(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromSequence must be <= toSequence");

        verify(eventRepository, never()).findByTenantIdAndSequenceNumberBetween(any(), any(Long.class), any(Long.class));
    }

    @Test
    @DisplayName("Export throws IllegalStateException when no events in range")
    void exportBundle_noEventsInRange_throwsIllegalState() {
        when(eventRepository.findByTenantIdAndSequenceNumberBetween(TENANT_ID, 100L, 200L))
                .thenReturn(List.of());

        AuditExportRequest request = buildExportRequest(100L, 200L);

        assertThatThrownBy(() -> auditExportService.createExport(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No audit events found");
    }

    @Test
    @DisplayName("Export throws IllegalStateException when signing service fails")
    void exportBundle_signingServiceFailure_throwsIllegalState() {
        String hash = "6666666666666666666666666666666666666666666666666666666666666666";
        AuditEventEntity event = buildEventWithHash(TENANT_ID, 1L, hash);

        when(eventRepository.findByTenantIdAndSequenceNumberBetween(TENANT_ID, 1L, 1L))
                .thenReturn(List.of(event));

        when(restTemplate.postForEntity(
                eq(KEYS_BASE_URL + SIGN_ENDPOINT),
                any(SignRequest.class),
                eq(SignResponse.class)))
                .thenThrow(new RestClientException("Connection refused"));

        AuditExportRequest request = buildExportRequest(1L, 1L);

        assertThatThrownBy(() -> auditExportService.createExport(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to sign audit export bundle");
    }

    @Test
    @DisplayName("Export throws IllegalStateException when signing service returns null signature")
    void exportBundle_nullSignature_throwsIllegalState() {
        String hash = "7777777777777777777777777777777777777777777777777777777777777777";
        AuditEventEntity event = buildEventWithHash(TENANT_ID, 1L, hash);

        when(eventRepository.findByTenantIdAndSequenceNumberBetween(TENANT_ID, 1L, 1L))
                .thenReturn(List.of(event));

        SignResponse emptyResponse = new SignResponse(null, null, "Ed25519");
        when(restTemplate.postForEntity(
                eq(KEYS_BASE_URL + SIGN_ENDPOINT),
                any(SignRequest.class),
                eq(SignResponse.class)))
                .thenReturn(ResponseEntity.ok(emptyResponse));

        AuditExportRequest request = buildExportRequest(1L, 1L);

        assertThatThrownBy(() -> auditExportService.createExport(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty signature");
    }

    // -----------------------------------------------------------------------
    // getExport tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getExport returns export response when found for tenant")
    void getExport_found_returnsResponse() {
        AuditExportEntity entity = buildExportEntity("bundlehash123", 1L, 10L, 10);
        when(exportRepository.findByIdAndTenantId(EXPORT_ID, TENANT_ID))
                .thenReturn(Optional.of(entity));

        Optional<AuditExportResponse> result = auditExportService.getExport(TENANT_ID, EXPORT_ID);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(EXPORT_ID);
        assertThat(result.get().tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.get().bundleHash()).isEqualTo("bundlehash123");
        assertThat(result.get().signature()).isEqualTo(MOCK_SIGNATURE);
        assertThat(result.get().entryCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("getExport returns empty when export not found or tenant mismatch")
    void getExport_notFound_returnsEmpty() {
        when(exportRepository.findByIdAndTenantId(EXPORT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        Optional<AuditExportResponse> result = auditExportService.getExport(TENANT_ID, EXPORT_ID);

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // verifyExport tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("verifyExport returns success when bundle hash matches recomputed hash")
    void verifyExport_intactBundle_returnsSuccess() {
        String hash1 = "aaaa0000aaaa0000aaaa0000aaaa0000aaaa0000aaaa0000aaaa0000aaaa0000";
        String hash2 = "bbbb0000bbbb0000bbbb0000bbbb0000bbbb0000bbbb0000bbbb0000bbbb0000";
        String bundleHash = computeExpectedBundleHash(List.of(hash1, hash2));

        AuditExportEntity export = buildExportEntity(bundleHash, 1L, 2L, 2);
        when(exportRepository.findByIdAndTenantId(EXPORT_ID, TENANT_ID))
                .thenReturn(Optional.of(export));

        AuditEventEntity event1 = buildEventWithHash(TENANT_ID, 1L, hash1);
        AuditEventEntity event2 = buildEventWithHash(TENANT_ID, 2L, hash2);
        when(eventRepository.findByTenantIdAndSequenceNumberBetween(TENANT_ID, 1L, 2L))
                .thenReturn(List.of(event1, event2));

        ExportVerificationResponse result = auditExportService.verifyExport(TENANT_ID, EXPORT_ID);

        assertThat(result.intact()).isTrue();
        assertThat(result.signatureValid()).isTrue();
        assertThat(result.expectedEntryCount()).isEqualTo(2);
        assertThat(result.actualEntryCount()).isEqualTo(2);
        assertThat(result.message()).contains("verified");
    }

    @Test
    @DisplayName("verifyExport returns not found when export does not exist")
    void verifyExport_notFound_returnsNotFound() {
        when(exportRepository.findByIdAndTenantId(EXPORT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        ExportVerificationResponse result = auditExportService.verifyExport(TENANT_ID, EXPORT_ID);

        assertThat(result.intact()).isFalse();
        assertThat(result.exportId()).isEqualTo(EXPORT_ID);
        assertThat(result.message()).contains("not found");
    }

    @Test
    @DisplayName("verifyExport returns hash mismatch when entry count differs")
    void verifyExport_entryCountMismatch_returnsFailure() {
        String hash1 = "cccc0000cccc0000cccc0000cccc0000cccc0000cccc0000cccc0000cccc0000";
        String bundleHash = computeExpectedBundleHash(List.of(hash1));

        // Export says 2 entries, but only 1 exists now
        AuditExportEntity export = buildExportEntity(bundleHash, 1L, 2L, 2);
        when(exportRepository.findByIdAndTenantId(EXPORT_ID, TENANT_ID))
                .thenReturn(Optional.of(export));

        AuditEventEntity event1 = buildEventWithHash(TENANT_ID, 1L, hash1);
        when(eventRepository.findByTenantIdAndSequenceNumberBetween(TENANT_ID, 1L, 2L))
                .thenReturn(List.of(event1));

        ExportVerificationResponse result = auditExportService.verifyExport(TENANT_ID, EXPORT_ID);

        assertThat(result.intact()).isFalse();
        assertThat(result.expectedEntryCount()).isEqualTo(2);
        assertThat(result.actualEntryCount()).isEqualTo(1);
        assertThat(result.message()).contains("mismatch");
    }

    @Test
    @DisplayName("verifyExport returns hash mismatch when event hashes have been tampered")
    void verifyExport_tamperedHashes_returnsFailure() {
        String originalHash = "dddd0000dddd0000dddd0000dddd0000dddd0000dddd0000dddd0000dddd0000";
        String bundleHash = computeExpectedBundleHash(List.of(originalHash));

        AuditExportEntity export = buildExportEntity(bundleHash, 1L, 1L, 1);
        when(exportRepository.findByIdAndTenantId(EXPORT_ID, TENANT_ID))
                .thenReturn(Optional.of(export));

        // Return event with different entry hash (tampered)
        String tamperedHash = "eeee0000eeee0000eeee0000eeee0000eeee0000eeee0000eeee0000eeee0000";
        AuditEventEntity tamperedEvent = buildEventWithHash(TENANT_ID, 1L, tamperedHash);
        when(eventRepository.findByTenantIdAndSequenceNumberBetween(TENANT_ID, 1L, 1L))
                .thenReturn(List.of(tamperedEvent));

        ExportVerificationResponse result = auditExportService.verifyExport(TENANT_ID, EXPORT_ID);

        assertThat(result.intact()).isFalse();
        assertThat(result.message()).contains("mismatch");
    }
}
