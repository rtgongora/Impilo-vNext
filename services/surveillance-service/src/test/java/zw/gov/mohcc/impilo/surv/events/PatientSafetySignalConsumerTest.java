package zw.gov.mohcc.impilo.surv.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.surv.core.IngestService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PatientSafetySignalConsumerTest {

    private static final UUID FALLBACK_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String TOPIC = "impilo.patient_safety.report.serious.v1";

    @Mock private IngestService ingestService;

    private PatientSafetySignalConsumer consumer() {
        return new PatientSafetySignalConsumer(ingestService, new ObjectMapper(), new SimpleMeterRegistry());
    }

    @Test
    void ingests_a_valid_signal_with_parsed_identifiers() {
        UUID tenant = UUID.randomUUID();
        UUID facility = UUID.randomUUID();
        UUID correlation = UUID.randomUUID();
        String msg = String.format("{\"tenantId\":\"%s\",\"facilityId\":\"%s\",\"correlationId\":\"%s\"}",
                tenant, facility, correlation);

        consumer().consumePatientSafetySignal(msg, TOPIC);

        verify(ingestService).ingest(eq(tenant), eq("kafka:patient-safety"), eq(correlation),
                eq(TOPIC), eq(msg), eq(facility), eq("kafka:" + TOPIC + ":" + correlation));
    }

    @Test
    void falls_back_to_the_default_tenant_when_absent() {
        consumer().consumePatientSafetySignal("{\"facilityId\":null}", TOPIC);
        ArgumentCaptor<UUID> tenant = ArgumentCaptor.forClass(UUID.class);
        verify(ingestService).ingest(tenant.capture(), any(), any(), any(), any(), any(), any());
        assertThat(tenant.getValue()).isEqualTo(FALLBACK_TENANT);
    }

    @Test
    void accepts_snake_case_field_names() {
        UUID tenant = UUID.randomUUID();
        consumer().consumePatientSafetySignal("{\"tenant_id\":\"" + tenant + "\"}", TOPIC);
        verify(ingestService).ingest(eq(tenant), any(), any(), any(), any(), any(), any());
    }

    @Test
    void malformed_json_is_swallowed_and_not_ingested() {
        // The consumer must not throw (it would poison the Kafka listener) and must not ingest garbage.
        consumer().consumePatientSafetySignal("not-json {{", TOPIC);
        verify(ingestService, never()).ingest(any(), any(), any(), any(), any(), any(), any());
    }
}
