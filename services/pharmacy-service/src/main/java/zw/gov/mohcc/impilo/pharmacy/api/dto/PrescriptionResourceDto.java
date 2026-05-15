package zw.gov.mohcc.impilo.pharmacy.api.dto;

import zw.gov.mohcc.impilo.pharmacy.persistence.entity.PrescriptionEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JSON:API style prescription resource consumed by Experience shells.
 */
public record PrescriptionResourceDto(
        UUID id,
        String type,
        Map<String, Object> attributes
) {
    public static PrescriptionResourceDto from(PrescriptionEntity entity) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", entity.getPatientCpid());
        attributes.put("encounter_id", entity.getEncounterId());
        attributes.put("facility_id", entity.getFacilityId() != null ? entity.getFacilityId().toString() : null);
        attributes.put("medication_name", entity.getMedicationName());
        attributes.put("generic_name", entity.getGenericName());
        attributes.put("dosage", entity.getDosage());
        attributes.put("route", entity.getRoute());
        attributes.put("frequency", entity.getFrequency());
        attributes.put("duration", entity.getDuration());
        attributes.put("quantity", entity.getQuantity());
        attributes.put("instructions", entity.getInstructions());
        attributes.put("indication", entity.getIndication());
        attributes.put("prescribed_by", entity.getPrescribedBy());
        attributes.put("status", entity.getStatus().name());
        attributes.put("cancel_reason", entity.getCancelReason());
        attributes.put("cancelled_by", entity.getCancelledBy());
        attributes.put("cancelled_at", entity.getCancelledAt());
        attributes.put("dispensed_by", entity.getDispensedBy());
        attributes.put("dispensed_at", entity.getDispensedAt());
        attributes.put("refill_requested_at", entity.getRefillRequestedAt());
        attributes.put("created_at", entity.getCreatedAt());
        attributes.put("updated_at", entity.getUpdatedAt());
        return new PrescriptionResourceDto(entity.getPrescriptionId(), "prescription", attributes);
    }
}
