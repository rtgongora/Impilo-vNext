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
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirAuditLogEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.FhirAuditLogRepository;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.FhirRouteRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The gateway must say what the destination actually answered.
 *
 * <p>{@code FhirForwarder.ForwardAttempt} has always carried the downstream HTTP status, and
 * {@code GatewayForwardService} read only {@code attempt.delivered()} and discarded it. A 403
 * (missing trust headers), a 409 (duplicate), a 422 (PII violation) and a 500 were therefore the
 * same audit row and the same response.</p>
 *
 * <p>That is not a reporting nicety. <b>offline-edge's entire conflict-review path turns on
 * distinguishing HTTP 409 from a transport failure</b> — a 409 becomes a {@code ConflictReviewEntity}
 * and a reconciliation event, anything else marks the replayed action FAILED. Routed through this
 * gateway, every 409 it saw would degrade silently to FAILED and the conflict would be lost. P5
 * cannot migrate offline-edge onto the gateway until this holds.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ForwardDeliveryEvidenceTest {

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
                consentEnforcementService, fhirForwarder, TARGET);
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
        return service.forward(TENANT, "actor-1", UUID.randomUUID(), null,
                "Observation", "CREATE", "{}", "cpid-1", "TREATMENT");
    }

    private FhirAuditLogEntity savedAudit() {
        ArgumentCaptor<FhirAuditLogEntity> cap = ArgumentCaptor.forClass(FhirAuditLogEntity.class);
        verify(auditLogRepository, atLeastOnce()).save(cap.capture());
        return cap.getValue();
    }

    @Test
    void aConflictIsDistinguishableFromATransportFailure() {
        // The one that matters: offline-edge turns a 409 into a conflict review and anything else
        // into FAILED. If both arrive as FORWARD_FAILED with no status, the conflict is lost.
        var conflict = forwardWith(new FhirForwarder.ForwardAttempt(false, 409, "downstream error"));
        assertThat(conflict.downstreamStatus()).isEqualTo(409);

        reset(auditLogRepository);
        when(auditLogRepository.save(any())).thenAnswer(i -> {
            FhirAuditLogEntity e = i.getArgument(0);
            e.setId(2L);
            return e;
        });

        var unreachable = forwardWith(new FhirForwarder.ForwardAttempt(false, 0, "transport error"));
        assertThat(unreachable.downstreamStatus())
                .describedAs("0 is not an HTTP status — a transport failure must stay "
                        + "distinguishable from one the server actually sent")
                .isNull();

        assertThat(conflict.outcome()).isEqualTo(unreachable.outcome()); // both FORWARD_FAILED...
        assertThat(conflict.downstreamStatus()).isNotEqualTo(unreachable.downstreamStatus());
    }

    @Test
    void aRefusalAndAnOutageAreDifferentAuditRows() {
        // "The SHR rejected this resource as malformed" and "the SHR was unreachable" are opposite
        // operational problems and used to be the same row.
        forwardWith(new FhirForwarder.ForwardAttempt(false, 422, "downstream error"));

        FhirAuditLogEntity audit = savedAudit();
        assertThat(audit.getDownstreamStatus()).isEqualTo(422);
        assertThat(audit.getOutcome()).isEqualTo("FORWARD_FAILED");
    }

    @Test
    void theAuditRowRecordsWhereItWasSent() {
        // The delivery target is one environment variable and has been wrong before — it pointed at
        // the ungoverned stock HAPI server. An audit row that cannot say where it delivered cannot
        // prove where it did not.
        forwardWith(new FhirForwarder.ForwardAttempt(true, 201, "forwarded"));

        FhirAuditLogEntity audit = savedAudit();
        assertThat(audit.getTargetEndpoint()).isEqualTo(TARGET + "/Observation");
        assertThat(audit.getDownstreamStatus()).isEqualTo(201);
    }

    @Test
    void theAuditRowRecordsWhoseWriteItWas() {
        // A consent denial is unanswerable without the subject. CPID only — never PII.
        when(consentEnforcementService.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(ConsentOutcome.DENY);

        service.forward(TENANT, "actor-1", UUID.randomUUID(), null,
                "Observation", "CREATE", "{}", "cpid-42", "TREATMENT");

        FhirAuditLogEntity audit = savedAudit();
        assertThat(audit.getSubjectCpid()).isEqualTo("cpid-42");
        assertThat(audit.getOutcome()).isEqualTo("CONSENT_DENIED");
        assertThat(audit.getDownstreamStatus())
                .describedAs("nothing was sent, so there is no downstream status — null here means "
                        + "'never called', which is the distinction the column exists for")
                .isNull();
    }

    @Test
    void aSuccessfulForwardCarriesItsStatusToo() {
        var result = forwardWith(new FhirForwarder.ForwardAttempt(true, 201, "forwarded"));

        assertThat(result.outcome()).isEqualTo("SUCCESS");
        assertThat(result.downstreamStatus()).isEqualTo(201);
        assertThat(result.targetEndpoint()).isEqualTo(TARGET + "/Observation");
    }
}
