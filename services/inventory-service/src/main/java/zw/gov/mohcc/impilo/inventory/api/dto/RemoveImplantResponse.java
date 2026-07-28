package zw.gov.mohcc.impilo.inventory.api.dto;

import java.util.UUID;

/** Result of recording an implant removal or revision. */
public record RemoveImplantResponse(UUID patientImplantId, String status, String removalReason) {}
