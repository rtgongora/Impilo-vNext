package zw.gov.mohcc.impilo.inpatient.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inpatient.core.AdmissionService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InpatientEventConsumer#consumePctAdmission} retry/poison-pill semantics (M2).
 */
@ExtendWith(MockitoExtension.class)
class InpatientEventConsumerTest {

    @Mock
    private AdmissionService admissionService;

    private InpatientEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InpatientEventConsumer(new ObjectMapper(), admissionService);
    }

    private String approvedEvent(String pctAdmissionId, String tenantId, String facilityId) {
        return "{"
                + "\"event_type\":\"ADMISSION_APPROVED\","
                + "\"payload\":{"
                + "  \"admissionId\":\"" + pctAdmissionId + "\","
                + "  \"tenantId\":\"" + tenantId + "\","
                + "  \"subjectCpid\":\"CPID-ZW-1\","
                + "  \"facilityId\":\"" + facilityId + "\""
                + "}}";
    }

    @Test
    void transientFailure_isRethrown_soKafkaRedelivers() {
        String message = approvedEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
        when(admissionService.admitFromPctApproval(any(), any(), any(), anyString(), any(), any(), any(),
                any(), any()))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("db unavailable"));

        // Pre-fix this RuntimeException was swallowed and the offset committed -> admission silently dropped.
        // Now it must propagate so the broker redelivers (admitFromPctApproval is idempotent on C1).
        assertThatThrownBy(() -> consumer.consumePctAdmission(message))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void malformedUuid_isAcked_notRetried() {
        String message = approvedEvent("not-a-uuid", UUID.randomUUID().toString(), UUID.randomUUID().toString());

        // A poison pill must NOT throw (no infinite redelivery loop); it is skipped.
        assertThatCode(() -> consumer.consumePctAdmission(message)).doesNotThrowAnyException();
        verify(admissionService, never()).admitFromPctApproval(any(), any(), any(), anyString(), any(), any(),
                any(), any(), any());
    }

    @Test
    void unparseableJson_isAcked_notRetried() {
        assertThatCode(() -> consumer.consumePctAdmission("{not json"))
                .doesNotThrowAnyException();
        verify(admissionService, never()).admitFromPctApproval(any(), any(), any(), anyString(), any(), any(),
                any(), any(), any());
    }

    @Test
    void nonApprovalEvent_isIgnored() {
        String message = "{\"event_type\":\"ADMISSION_REQUESTED\",\"payload\":{}}";
        assertThatCode(() -> consumer.consumePctAdmission(message)).doesNotThrowAnyException();
        verify(admissionService, never()).admitFromPctApproval(any(), any(), any(), anyString(), any(), any(),
                any(), any(), any());
    }

    @Test
    void happyPath_materialisesAdmission() {
        UUID pct = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID facility = UUID.randomUUID();
        String message = approvedEvent(pct.toString(), tenant.toString(), facility.toString());

        consumer.consumePctAdmission(message);

        verify(admissionService).admitFromPctApproval(eqUuid(pct), eqUuid(tenant), eqUuid(facility),
                anyString(), any(), any(), any(), any(), any());
    }

    private static UUID eqUuid(UUID expected) {
        return org.mockito.ArgumentMatchers.eq(expected);
    }
}
