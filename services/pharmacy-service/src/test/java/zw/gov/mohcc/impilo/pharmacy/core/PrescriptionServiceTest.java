package zw.gov.mohcc.impilo.pharmacy.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.pharmacy.api.dto.CreatePrescriptionRequest;
import zw.gov.mohcc.impilo.pharmacy.domain.PrescriptionStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class PrescriptionServiceTest {

    @Autowired
    private PrescriptionService prescriptionService;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean
    private EventOutboxRepository outboxRepository;

    @AfterEach
    void clearContext() {
        TrustContextHolder.clear();
    }

    @Test
    void createAndCancelPrescriptionPersistsStateAndOutbox() {
        UUID tenantId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();
        TrustContextHolder.set(new TrustContext(
                tenantId,
                "provider-1",
                "PROVIDER",
                "TREATMENT",
                null,
                UUID.randomUUID(),
                facilityId,
                UUID.randomUUID(),
                null,
                AccessMode.INTERNAL));

        var created = prescriptionService.createPrescription(new CreatePrescriptionRequest(
                "cpid-1",
                "enc-1",
                facilityId.toString(),
                "Amoxicillin",
                "Amoxicillin",
                "500mg",
                "oral",
                "TID",
                "7 days",
                21,
                "Take with food",
                "URI",
                "provider-1"
        ));

        assertThat(created.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
        assertThat(created.getPrescriptionId()).isNotNull();

        var cancelled = prescriptionService.cancelPrescription(created.getPrescriptionId(), "Patient request");
        assertThat(cancelled.getStatus()).isEqualTo(PrescriptionStatus.CANCELLED);
        assertThat(cancelled.getCancelReason()).isEqualTo("Patient request");
        assertThat(cancelled.getCancelledBy()).isEqualTo("provider-1");

    }

    @Test
    void cannotCancelDispensedPrescription() {
        UUID tenantId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();
        TrustContextHolder.set(new TrustContext(
                tenantId,
                "provider-1",
                "PROVIDER",
                "TREATMENT",
                null,
                UUID.randomUUID(),
                facilityId,
                UUID.randomUUID(),
                null,
                AccessMode.INTERNAL));

        var created = prescriptionService.createPrescription(new CreatePrescriptionRequest(
                "cpid-2",
                "enc-2",
                facilityId.toString(),
                "Ibuprofen",
                "Ibuprofen",
                "200mg",
                "oral",
                "BD",
                "5 days",
                10,
                null,
                null,
                "provider-1"
        ));

        prescriptionService.markDispensed(created.getPrescriptionId(), "pharm-1");

        assertThrows(IllegalStateException.class, () ->
                prescriptionService.cancelPrescription(created.getPrescriptionId(), "late cancel"));

        var refill = prescriptionService.requestRefill(created.getPrescriptionId(), Map.of("source", "citizen"));
        assertThat(refill.getRefillRequestedAt()).isNotNull();
    }
}
