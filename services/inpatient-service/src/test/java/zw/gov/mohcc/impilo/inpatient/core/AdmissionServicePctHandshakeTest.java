package zw.gov.mohcc.impilo.inpatient.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.AdmissionRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the PCT&lt;-&gt;inpatient admission handshake idempotency (C1 / M3).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AdmissionServicePctHandshakeTest {

    @Autowired
    private AdmissionService admissionService;

    @Autowired
    private AdmissionRepository admissionRepository;

    @Autowired
    private EventOutboxRepository outboxRepository;

    private long bedAssignedEchoCount(UUID pctAdmissionId) {
        return outboxRepository.findAll().stream()
                .filter(e -> "inpatient.admission.bed_assigned".equals(e.getEventType()))
                .filter(e -> e.getPayloadJson() != null && e.getPayloadJson().contains(pctAdmissionId.toString()))
                .count();
    }

    @Test
    void redeliveredApprovalAfterDischarge_doesNotDoubleAdmit_andReEmitsEcho() {
        UUID pctAdmissionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();
        String cpid = "CPID-ZW-HANDSHAKE-1";
        UUID wardId = UUID.randomUUID();
        UUID bedId = UUID.randomUUID();

        // 1. First ADMISSION_APPROVED -> census admission materialised + bed_assigned echo emitted.
        AdmissionEntity first = admissionService.admitFromPctApproval(
                pctAdmissionId, tenantId, facilityId, cpid, null, wardId, bedId, "Pneumonia", "PLANNED");
        assertThat(first.getStatus()).isEqualTo("ADMITTED");
        assertThat(bedAssignedEchoCount(pctAdmissionId)).isEqualTo(1L);

        // 2. Patient is discharged.
        admissionService.dischargePatient(first.getAdmissionRef());

        // 3. The SAME ADMISSION_APPROVED is redelivered after discharge. Pre-fix this created a SECOND
        //    census row (double bed-day) because the idempotency guard only matched ADMITTED rows.
        AdmissionEntity second = admissionService.admitFromPctApproval(
                pctAdmissionId, tenantId, facilityId, cpid, null, wardId, bedId, "Pneumonia", "PLANNED");

        // Same census row returned — no double-admit.
        assertThat(second.getAdmissionRef()).isEqualTo(first.getAdmissionRef());
        List<AdmissionEntity> rows = admissionRepository.findBySubjectCpid(cpid).stream()
                .filter(a -> pctAdmissionId.equals(a.getPctAdmissionId()))
                .toList();
        assertThat(rows).hasSize(1);

        // M3: the echo is re-emitted on the idempotent short-circuit so PCT can still stamp its link if the
        // first echo was lost.
        assertThat(bedAssignedEchoCount(pctAdmissionId)).isEqualTo(2L);
    }
}
