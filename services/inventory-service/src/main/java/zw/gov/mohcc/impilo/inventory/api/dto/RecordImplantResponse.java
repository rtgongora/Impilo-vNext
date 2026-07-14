package zw.gov.mohcc.impilo.inventory.api.dto;

import java.util.UUID;

/**
 * Result of recording an implanted device unit.
 */
public record RecordImplantResponse(
        UUID implantUnitId,
        UUID patientImplantId,
        String udi,
        String serialNumber,
        String lotNumber,
        String status
) {}
