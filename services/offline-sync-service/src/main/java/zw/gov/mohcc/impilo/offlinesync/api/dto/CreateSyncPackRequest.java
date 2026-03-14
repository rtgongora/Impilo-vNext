package zw.gov.mohcc.impilo.offlinesync.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating a new sync pack.
 */
public class CreateSyncPackRequest {

    @NotNull
    private UUID tenantId;

    @NotNull
    private UUID facilityId;

    @NotBlank
    private String deviceId;

    private String payload;

    // Getters and setters

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
