package zw.gov.mohcc.impilo.oros.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the FHIR ImagingStudy-outbound adapter ({@link ButanoIntegration#createImagingStudy}):
 * flag-gated (NOT_LIVE by default) + the enabled-branch builds a valid R4 resource and hands it to
 * the governed gateway rather than posting it at BUTANO directly.
 */
@ExtendWith(MockitoExtension.class)
class ButanoImagingStudyTest {

    @Mock private FhirGatewayClient gateway;

    private OrderEntity imagingOrder() {
        OrderEntity o = new OrderEntity();
        o.setOrderId("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        o.setOrderType(OrderType.IMAGING);
        o.setPatientCpid("CPID-1");
        o.setImagingState(ImagingWorkflowState.IMAGES_LINKED);
        o.setStudyUid("1.2.840.113619.2.55");
        o.setAccessionNumber("ACC-2026-AB-1");
        o.setStudyViewerUrl("https://viewer/launch?s=1");
        return o;
    }

    @Test
    @DisplayName("disabled (default): no-op, nothing forwarded — honest NOT_LIVE seam")
    void disabledNoOp() {
        ButanoIntegration b = new ButanoIntegration(gateway, orderRepoWithCpid(), false);

        assertThat(b.createImagingStudy(imagingOrder(), "CT")).isNull();
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("enabled: forwards a FHIR R4 ImagingStudy with study UID + accession identifiers")
    void enabledForwardsImagingStudy() {
        ButanoIntegration b = new ButanoIntegration(gateway, orderRepoWithCpid(), true);
        when(gateway.forward(eq("ImagingStudy"), eq("CREATE"), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FhirGatewayClient.Result(
                        FhirGatewayClient.Outcome.SUCCESS, "77", "1041", "SUCCESS"));

        String ref = b.createImagingStudy(imagingOrder(), "CT");

        assertThat(ref)
                .describedAs("the caller persists the SHR's own logical id, which only the gateway "
                        + "can hand back")
                .isEqualTo("77");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(gateway).forward(eq("ImagingStudy"), eq("CREATE"), captor.capture(),
                eq("CPID-1"), any(), any(), any(), eq("oros-study-1.2.840.113619.2.55"));
        Map<String, Object> body = captor.getValue();

        assertThat(body).containsEntry("resourceType", "ImagingStudy").containsEntry("status", "available");
        assertThat(body.get("identifier").toString()).contains("urn:oid:1.2.840.113619.2.55").contains("ACC-2026-AB-1");
        // The order is carried as a business identifier, NOT as basedOn: ServiceRequest/{orderId}.
        // BUTANO creates no ServiceRequest and enforces referential integrity on write, so that
        // reference dangled and refused the whole resource (HAPI-1094). The identifier loses
        // nothing — the order is still recoverable and searchable.
        assertThat(body).doesNotContainKey("basedOn");
        assertThat(body.get("identifier").toString())
                .contains("https://impilo.gov.zw/oros/order-id")
                .contains("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        // And it now has a subject at all, as a match URL against the SHR's CPID system.
        assertThat(body.get("subject").toString())
                .contains("Patient?identifier=https://impilo.gov.zw/cpid|");
    }

    @Test
    @DisplayName("a refused forward yields no reference — never a fabricated one")
    void refusedForwardYieldsNoReference() {
        ButanoIntegration b = new ButanoIntegration(gateway, orderRepoWithCpid(), true);
        when(gateway.forward(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FhirGatewayClient.Result(
                        FhirGatewayClient.Outcome.CONSENT_DENIED, null, "1042", "consent=DENY"));

        assertThat(b.createImagingStudy(imagingOrder(), "CT")).isNull();
    }

    @Test
    @DisplayName("enabled but no linked study: no-op")
    void enabledNoStudyNoOp() {
        ButanoIntegration b = new ButanoIntegration(gateway, orderRepoWithCpid(), true);
        OrderEntity o = imagingOrder();
        o.setStudyUid(null);

        assertThat(b.createImagingStudy(o, "CT")).isNull();
        verifyNoInteractions(gateway);
    }

    /**
     * An order repository that resolves the subject, so these tests exercise the real path.
     *
     * <p>DiagnosticReport and Observation used to carry no subject at all — the CPID is on
     * OrderEntity and neither method received it. It is resolved from the order id now.</p>
     */
    private static zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository orderRepoWithCpid() {
        zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository repo = mock(
                zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository.class);
        OrderEntity order = new OrderEntity();
        order.setPatientCpid("c08ba747-26ff-4f19-b712-76561505e274");
        lenient().when(repo.findByOrderId(anyString())).thenReturn(java.util.Optional.of(order));
        return repo;
    }
}
