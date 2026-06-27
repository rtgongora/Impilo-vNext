package zw.gov.mohcc.impilo.fhirgateway.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirAuditLogEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirRouteEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.FhirAuditLogRepository;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.FhirRouteRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Proves the F1 fix (G…): the gateway records SUCCESS only when the resource is ACTUALLY
 * forwarded (a real 2xx from downstream). Before the fix SUCCESS was recorded for any matched
 * route without any HTTP call — the resource was never delivered to BUTANO/HAPI.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GatewayForwardServiceTest {

    @Mock private FhirRouteRepository routeRepository;
    @Mock private FhirAuditLogRepository auditLogRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ConsentEnforcementService consentEnforcementService;
    @Mock private FhirForwarder fhirForwarder;

    private GatewayForwardService service;
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GatewayForwardService(routeRepository, auditLogRepository, outboxRepository,
                consentEnforcementService, fhirForwarder);

        when(consentEnforcementService.evaluate(any(), any(), any(), any(), any()))
                .thenReturn(ConsentOutcome.PERMIT);
        FhirRouteEntity route = new FhirRouteEntity();
        route.setTenantId(tenant);
        route.setResourceType("Patient");
        route.setTargetEndpoint("http://butano/fhir/Patient");
        route.setEnabled(true);
        when(routeRepository.findByTenantIdAndResourceTypeAndEnabledTrue(any(), anyString()))
                .thenReturn(List.of(route));
        when(auditLogRepository.save(any())).thenAnswer(inv -> {
            FhirAuditLogEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(1L);
            return e;
        });
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private GatewayForwardService.ForwardResult forwardPatient() {
        return service.forward(tenant, "actor-1", UUID.randomUUID(), "10.0.0.1",
                "Patient", "CREATE", "{\"resourceType\":\"Patient\"}", "cpid-1", "TREATMENT");
    }

    @Test
    void recordsSuccess_onlyWhenActuallyDelivered() {
        when(fhirForwarder.send(any(), any(), any(), any()))
                .thenReturn(new FhirForwarder.ForwardAttempt(true, 201, "forwarded"));

        assertThat(forwardPatient().outcome()).isEqualTo("SUCCESS");
    }

    @Test
    void recordsForwardFailed_whenDownstreamRejects_noFabricatedSuccess() {
        when(fhirForwarder.send(any(), any(), any(), any()))
                .thenReturn(new FhirForwarder.ForwardAttempt(false, 500, "downstream error"));

        // The old code returned SUCCESS here without ever forwarding. Now it must NOT.
        assertThat(forwardPatient().outcome()).isEqualTo("FORWARD_FAILED");
    }
}
