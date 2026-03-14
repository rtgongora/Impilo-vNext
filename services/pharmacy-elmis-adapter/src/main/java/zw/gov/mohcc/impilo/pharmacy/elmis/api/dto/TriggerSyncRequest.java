package zw.gov.mohcc.impilo.pharmacy.elmis.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for triggering a dispense synchronization run.
 */
public class TriggerSyncRequest {

    @NotNull
    private UUID facilityId;

    @NotNull
    private String syncType;

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getSyncType() { return syncType; }
    public void setSyncType(String syncType) { this.syncType = syncType; }
}
