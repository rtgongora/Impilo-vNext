package zw.gov.mohcc.impilo.fhirgateway.core;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirAuditLogEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.FhirAuditLogRepository;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.FhirRouteRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The gateway must be able to name what it created.
 *
 * <p>{@code FhirForwarder} read the downstream response only to decide 2xx-or-not, and discarded
 * both the {@code Location} header and the returned resource body. So a caller writing through the
 * governed seam got back an audit-log id and no way to reference the resource it had just written.
 * That is why every direct-HTTP writer in the estate kept its own ungoverned path: oros persists a
 * {@code butanoRef} against the order, inpatient links a Procedure, offline-edge reconciles against
 * the created resource — and none of them could have got one from here.</p>
 *
 * <p>Null stays meaningful. A refusal, an outage and a destination that simply did not say are all
 * "no id", and none of them may be papered over with a guess.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ForwardResourceIdTest {

    // ── The extraction itself ─────────────────────────────────────────────────────────────────

    private static ResponseEntity<String> response(HttpStatus status, String location, String body) {
        HttpHeaders headers = new HttpHeaders();
        if (location != null) {
            headers.set(HttpHeaders.LOCATION, location);
        }
        return new ResponseEntity<>(body, headers, status);
    }

    @Test
    void theLocationHeaderYieldsTheLogicalIdWithoutItsVersion() {
        // HAPI answers Location: {base}/{type}/{id}/_history/{version}. The version is not the id —
        // storing "3/_history/1" as a reference would fail to resolve on every subsequent read.
        String id = FhirForwarder.extractResourceId(response(HttpStatus.CREATED,
                "http://butano-service:8090/fhir/Observation/3/_history/1", null));

        assertThat(id).isEqualTo("3");
    }

    @Test
    void aLocationWithNoHistorySegmentStillYieldsTheId() {
        assertThat(FhirForwarder.extractResourceId(response(HttpStatus.CREATED,
                "http://butano-service:8090/fhir/DiagnosticReport/abc-123", null)))
                .isEqualTo("abc-123");
    }

    @Test
    void theBodyIsReadWhenThereIsNoLocationHeader() {
        // Prefer: return=minimal suppresses the body; a proxy may drop Location. Neither source is
        // guaranteed, so both are read rather than trusting one.
        assertThat(FhirForwarder.extractResourceId(response(HttpStatus.CREATED, null,
                "{\"resourceType\":\"Observation\",\"id\":\"from-body\"}")))
                .isEqualTo("from-body");
    }

    @Test
    void aDestinationThatSaysNothingYieldsNull_notAnInventedId() {
        assertThat(FhirForwarder.extractResourceId(response(HttpStatus.OK, null, null))).isNull();
        assertThat(FhirForwarder.extractResourceId(response(HttpStatus.OK, null, "not json")))
                .isNull();
        assertThat(FhirForwarder.extractResourceId(response(HttpStatus.OK, null, "{\"id\":\"\"}")))
                .isNull();
    }

    // ── send() must actually call it ──────────────────────────────────────────────────────────

    /**
     * The extraction tests above pass with {@code send()} completely broken — they call the helper
     * directly, and every other test here mocks {@link FhirForwarder} away. Nothing exercised the
     * one line that connects them. These two do: a real forwarder over a stubbed RestTemplate.
     */
    private static FhirForwarder forwarderReturning(ResponseEntity<String> downstream) {
        org.springframework.web.client.RestTemplate rest =
                mock(org.springframework.web.client.RestTemplate.class);
        when(rest.exchange(anyString(), any(org.springframework.http.HttpMethod.class),
                any(), eq(String.class))).thenReturn(downstream);
        return new FhirForwarder(rest);
    }

    @Test
    void sendReadsTheIdOffTheRealResponse() {
        FhirForwarder.ForwardAttempt attempt = forwarderReturning(response(HttpStatus.CREATED,
                "http://butano-service:8090/fhir/Observation/9/_history/1", null))
                .send("http://butano-service:8090/fhir/Observation", "Observation", "CREATE", "{}");

        assertThat(attempt.delivered()).isTrue();
        assertThat(attempt.resourceId())
                .describedAs("send() must call the extraction, not merely be able to")
                .isEqualTo("9");
    }

    @Test
    void sendNamesNothingWhenTheDestinationRefused() {
        // A 422 body can still carry an "id" (an OperationOutcome, or an echo). Reading it would
        // name a resource that was never created.
        FhirForwarder.ForwardAttempt attempt = forwarderReturning(response(
                HttpStatus.UNPROCESSABLE_ENTITY, "http://butano-service:8090/fhir/Observation/9",
                "{\"resourceType\":\"OperationOutcome\",\"id\":\"9\"}"))
                .send("http://butano-service:8090/fhir/Observation", "Observation", "CREATE", "{}");

        assertThat(attempt.delivered()).isFalse();
        assertThat(attempt.resourceId()).isNull();
    }

    // ── Through the forward path ──────────────────────────────────────────────────────────────

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String TARGET = "http://butano-service:8090/fhir";

    @Mock private FhirRouteRepository routeRepository;
    @Mock private FhirAuditLogRepository auditLogRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ConsentEnforcementService consentEnforcementService;
    @Mock private FhirForwarder fhirForwarder;

    private GatewayForwardService service;

    @BeforeEach
    void setUp() {
        service = new GatewayForwardService(routeRepository, auditLogRepository, outboxRepository,
                consentEnforcementService, fhirForwarder,
                new ForwardAuditRecorder(auditLogRepository, outboxRepository), TARGET);
        when(routeRepository.findByTenantIdAndResourceTypeAndEnabledTrue(any(), any()))
                .thenReturn(List.of());
        when(consentEnforcementService.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ConsentOutcome.PERMIT);
        when(auditLogRepository.save(any())).thenAnswer(i -> {
            FhirAuditLogEntity e = i.getArgument(0);
            if (e.getId() == null) {
                e.setId(1L);
            }
            return e;
        });
    }

    private GatewayForwardService.ForwardResult forwardWith(FhirForwarder.ForwardAttempt attempt) {
        when(fhirForwarder.send(any(), any(), any(), any())).thenReturn(attempt);
        return service.forward(TENANT, "oros-service", UUID.randomUUID(), null,
                "DiagnosticReport", "CREATE", "{}", "cpid-1", "TREATMENT");
    }

    private FhirAuditLogEntity savedAudit() {
        ArgumentCaptor<FhirAuditLogEntity> cap = ArgumentCaptor.forClass(FhirAuditLogEntity.class);
        verify(auditLogRepository, atLeastOnce()).save(cap.capture());
        return cap.getValue();
    }

    @Test
    void aSuccessfulForwardHandsTheCallerTheResourceItCreated() {
        var result = forwardWith(
                new FhirForwarder.ForwardAttempt(true, 201, "forwarded", "shr-77"));

        assertThat(result.outcome()).isEqualTo("SUCCESS");
        assertThat(result.downstreamResourceId())
                .describedAs("without this the caller can record that it wrote, but not what — "
                        + "which is the gap that kept every writer on its own ungoverned path")
                .isEqualTo("shr-77");
    }

    @Test
    void theAuditRowNamesTheResourceTheForwardCreated() {
        forwardWith(new FhirForwarder.ForwardAttempt(true, 201, "forwarded", "shr-77"));

        assertThat(savedAudit().getDownstreamResourceId()).isEqualTo("shr-77");
    }

    @Test
    void aRefusedForwardNamesNoResource() {
        // A 422 created nothing. An id here would assert the existence of a record that does not
        // exist — worse than no id at all.
        var refused = forwardWith(new FhirForwarder.ForwardAttempt(false, 422, "downstream error"));

        assertThat(refused.outcome()).isEqualTo("FORWARD_FAILED");
        assertThat(refused.downstreamResourceId()).isNull();
        assertThat(savedAudit().getDownstreamResourceId()).isNull();
    }

    @Test
    void aConsentDeniedForwardNamesNoResource() {
        when(consentEnforcementService.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ConsentOutcome.DENY);

        var denied = service.forward(TENANT, "oros-service", UUID.randomUUID(), null,
                "DiagnosticReport", "CREATE", "{}", "cpid-1", "TREATMENT");

        assertThat(denied.outcome()).isEqualTo("CONSENT_DENIED");
        assertThat(denied.downstreamResourceId())
                .describedAs("nothing was sent, so there is nothing to name")
                .isNull();
        verify(fhirForwarder, never()).send(any(), any(), any(), any());
    }
}
