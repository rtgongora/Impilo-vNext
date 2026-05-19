package zw.gov.mohcc.impilo.pharmacy.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.pharmacy.api.dto.CreatePrescriptionRequest;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PrescriptionEntity;

import java.util.Map;
import java.util.UUID;

public interface PrescriptionService {
    PrescriptionEntity createPrescription(CreatePrescriptionRequest request);

    Page<PrescriptionEntity> listPatientPrescriptions(String patientCpid, String status, Pageable pageable);

    PrescriptionEntity getPrescription(UUID prescriptionId);

    PrescriptionEntity cancelPrescription(UUID prescriptionId, String reason);

    PrescriptionEntity requestRefill(UUID prescriptionId, Map<String, Object> payload);

    PrescriptionEntity markDispensed(UUID prescriptionId, String dispensedBy);
}
