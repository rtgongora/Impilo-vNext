package zw.gov.mohcc.impilo.varapi.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.core.RegisterMaterialisationService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The activation half of task #97: a council's registers follow its approved configuration without
 * anyone remembering to press a button.
 *
 * <p>Every envelope in this test is built by {@link #envelope}, which mirrors
 * {@code OrgRegistryOutboxPublisher} field for field. That is the point of the helper: the failure
 * this listener is most exposed to is not a wrong decision but a silent one — reading a key the
 * publisher does not write matches nothing, throws nothing, and is indistinguishable from a quiet
 * estate. If the publisher's envelope ever changes shape, these tests must be the thing that
 * notices.</p>
 */
@ExtendWith(MockitoExtension.class)
class ConfigReleaseActivatedListenerTest {

    @Mock private RegisterMaterialisationService materialisationService;

    private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID NCZ_ORG = UUID.fromString("a5000000-0000-4000-8000-000000000003");
    private static final UUID RELEASE = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private ConfigReleaseActivatedListener listener() {
        return new ConfigReleaseActivatedListener(new ObjectMapper(), materialisationService);
    }

    /**
     * The envelope {@code OrgRegistryOutboxPublisher.publishBatch} writes: camelCase envelope
     * fields wrapping the service's payload, with the event type built by {@code EventTopicRegistry}
     * as {@code impilo.{service}.{entity}.{action}.v1}.
     */
    private static String envelope(String aggregateType, String entity, String action,
                                   String payloadJson) {
        return "{\"eventType\":\"impilo.org-registry." + entity + "." + action + ".v1\","
                + "\"aggregateType\":\"" + aggregateType + "\","
                + "\"aggregateId\":\"" + RELEASE + "\","
                + "\"tenantId\":\"" + TENANT + "\","
                + "\"occurredAt\":\"2026-07-30T05:00:00Z\","
                + "\"payload\":" + payloadJson + "}";
    }

    private static String activationPayload(String organisationId) {
        String org = organisationId == null ? "null" : "\"" + organisationId + "\"";
        return "{\"packId\":\"33333333-3333-4333-8333-333333333333\","
                + "\"organisationId\":" + org + ","
                + "\"releaseId\":\"" + RELEASE + "\","
                + "\"releaseKey\":\"ncz-2026.1\",\"previousReleaseId\":null,"
                + "\"itemCount\":15,\"warnings\":0}";
    }

    private static RegisterMaterialisationService.ReconcileReport report() {
        return new RegisterMaterialisationService.ReconcileReport(
                "RECONCILED", "NCZ", 4, 1, 0, 3, 0, 0, List.of());
    }

    @Test
    void activatingAConfigurationReleaseReconcilesThatCouncilsRegisters() {
        when(materialisationService.reconcileForOrganisation(eq(TENANT), eq(NCZ_ORG), any()))
                .thenReturn(Optional.of(report()));

        listener().onOrgRegistryEvent(envelope("REGULATORY_CONFIG_RELEASE",
                "regulatory_config_release", "activated", activationPayload(NCZ_ORG.toString())));

        verify(materialisationService).reconcileForOrganisation(eq(TENANT), eq(NCZ_ORG), any());
    }

    @Test
    void anAppointmentEventOnTheSameTopicIsNotAConfigurationChange() {
        // tshepo-identity's appointment-teardown consumer shares this topic. Both listeners see
        // every message, so each must recognise only its own.
        listener().onOrgRegistryEvent(envelope("REGULATORY_APPOINTMENT",
                "regulatory_appointment", "ended",
                "{\"personHealthId\":\"HID-1\",\"organizationId\":\"" + NCZ_ORG + "\"}"));

        verify(materialisationService, never()).reconcileForOrganisation(any(), any(), any());
    }

    @Test
    void aReleaseThatIsOnlyApprovedDoesNotMaterialiseAnything() {
        // Rule 4. Registers follow approval taking effect, never authoring: a release that has been
        // drafted, reviewed or approved but not activated must create no register.
        listener().onOrgRegistryEvent(envelope("REGULATORY_CONFIG_RELEASE",
                "regulatory_config_release", "approved", activationPayload(NCZ_ORG.toString())));

        verify(materialisationService, never()).reconcileForOrganisation(any(), any(), any());
    }

    @Test
    void anEventWithNoOrganisationAnchorReconcilesNothingRatherThanGuessing() {
        // The pre-enrichment payload shape. Reconciling the wrong council can retire real
        // registers, so an event that does not say whose configuration it is must be declined.
        listener().onOrgRegistryEvent(envelope("REGULATORY_CONFIG_RELEASE",
                "regulatory_config_release", "activated", activationPayload(null)));

        verify(materialisationService, never()).reconcileForOrganisation(any(), any(), any());
    }

    @Test
    void anOrganisationThatRegulatesNoCouncilIsAQuietNoOp() {
        when(materialisationService.reconcileForOrganisation(eq(TENANT), eq(NCZ_ORG), any()))
                .thenReturn(Optional.empty());

        assertThatCode(() -> listener().onOrgRegistryEvent(envelope("REGULATORY_CONFIG_RELEASE",
                "regulatory_config_release", "activated", activationPayload(NCZ_ORG.toString()))))
                .doesNotThrowAnyException();
    }

    @Test
    void aPoisonedMessageDoesNotStallThePartition() {
        assertThatCode(() -> listener().onOrgRegistryEvent("{not json"))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener().onOrgRegistryEvent(null)).doesNotThrowAnyException();
        verify(materialisationService, never()).reconcileForOrganisation(any(), any(), any());
    }
}
