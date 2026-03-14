package zw.gov.mohcc.impilo.inventory.elmis.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for triggering an eLMIS sync operation.
 */
public class TriggerSyncRequest {

    @NotNull
    private UUID tenantId;

    @NotNull
    private UUID facilityId;

    @NotNull
    private String syncType;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public String getSyncType() { return syncType; }
    public void setSyncType(String syncType) { this.syncType = syncType; }
}
