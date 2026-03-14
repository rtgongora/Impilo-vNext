package zw.gov.mohcc.impilo.inpatient.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for transferring an admitted patient to a new ward/bed.
 */
public class TransferRequest {

    @NotNull
    private UUID toWardId;

    private UUID toBedId;

    public UUID getToWardId() { return toWardId; }
    public void setToWardId(UUID toWardId) { this.toWardId = toWardId; }

    public UUID getToBedId() { return toBedId; }
    public void setToBedId(UUID toBedId) { this.toBedId = toBedId; }
}
