package zw.gov.mohcc.impilo.oros.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the FHIR ImagingStudy-outbound adapter ({@link ButanoIntegration#createImagingStudy}):
 * flag-gated (NOT_LIVE by default) + the enabled-branch builds a valid R4 resource.
 */
@ExtendWith(MockitoExtension.class)
class ButanoImagingStudyTest {

    @Mock private RestTemplate restTemplate;

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
    @DisplayName("disabled (default): no-op, no HTTP — honest NOT_LIVE seam")
    void disabledNoOp() {
        ButanoIntegration b = new ButanoIntegration(restTemplate, orderRepoWithCpid(), "http://localhost:8090", false);

        assertThat(b.createImagingStudy(imagingOrder(), "CT")).isNull();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("enabled: POSTs a FHIR R4 ImagingStudy with study UID + accession identifiers")
    void enabledPostsImagingStudy() {
        ButanoIntegration b = new ButanoIntegration(restTemplate, orderRepoWithCpid(), "http://localhost:8090", true);
        when(restTemplate.postForEntity(eq("http://localhost:8090/fhir/ImagingStudy"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("id", "ImagingStudy/77")));

        String ref = b.createImagingStudy(imagingOrder(), "CT");

        assertThat(ref).isEqualTo("ImagingStudy/77");
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(any(String.class), captor.capture(), eq(Map.class));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getBody();
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
    @DisplayName("enabled but no linked study: no-op")
    void enabledNoStudyNoOp() {
        ButanoIntegration b = new ButanoIntegration(restTemplate, orderRepoWithCpid(), "http://localhost:8090", true);
        OrderEntity o = imagingOrder();
        o.setStudyUid(null);

        assertThat(b.createImagingStudy(o, "CT")).isNull();
        verifyNoInteractions(restTemplate);
    }

    /**
     * An order repository that resolves the subject, so these tests exercise the real path.
     *
     * <p>DiagnosticReport and Observation used to carry no subject at all — the CPID is on
     * OrderEntity and neither method received it. It is resolved from the order id now.</p>
     */
    private static zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository orderRepoWithCpid() {
        zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository repo = org.mockito.Mockito.mock(zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository.class);
        zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity order =
                new zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity();
        order.setPatientCpid("c08ba747-26ff-4f19-b712-76561505e274");
        org.mockito.Mockito.lenient()
                .when(repo.findByOrderId(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.of(order));
        return repo;
    }
}
