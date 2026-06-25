package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FhirServiceRequestService} — FHIR R4 ServiceRequest → normalized
 * {@link ExternalOrderIntake} handed to the shared intake service.
 */
@ExtendWith(MockitoExtension.class)
class FhirServiceRequestServiceTest {

    @Mock private ExternalOrderIntakeService intakeService;

    private final ObjectMapper om = new ObjectMapper();

    private JsonNode serviceRequest() throws Exception {
        return om.readTree("""
            {"resourceType":"ServiceRequest","priority":"stat",
             "identifier":[{"system":"urn:ext:lis","value":"EXT-123"}],
             "subject":{"reference":"Patient/CPID-1"},
             "category":[{"coding":[{"code":"imaging"}]}],
             "code":{"coding":[{"code":"CHEST-XR","display":"Chest X-Ray"}]},
             "note":[{"text":"Cough 2 weeks"}]}
            """);
    }

    @Test
    @DisplayName("parses a ServiceRequest into a normalized EXTERNAL intake and delegates")
    void parsesAndDelegates() throws Exception {
        OrderEntity placed = new OrderEntity();
        placed.setOrderId("ORD-NEW");
        placed.setStatus(OrderStatus.PLACED);
        when(intakeService.create(any(ExternalOrderIntake.class))).thenReturn(placed);

        OrderEntity result = new FhirServiceRequestService(intakeService).ingest(serviceRequest());

        assertThat(result.getOrderId()).isEqualTo("ORD-NEW");
        ArgumentCaptor<ExternalOrderIntake> in = ArgumentCaptor.forClass(ExternalOrderIntake.class);
        verify(intakeService).create(in.capture());
        ExternalOrderIntake v = in.getValue();
        assertThat(v.patientCpid()).isEqualTo("CPID-1");
        assertThat(v.orderType()).isEqualTo(OrderType.IMAGING);
        assertThat(v.priority()).isEqualTo(OrderPriority.STAT);
        assertThat(v.code()).isEqualTo("CHEST-XR");
        assertThat(v.displayName()).isEqualTo("Chest X-Ray");
        assertThat(v.clinicalNotes()).isEqualTo("Cough 2 weeks");
        assertThat(v.externalRef()).isEqualTo("urn:ext:lis|EXT-123");
    }
}
