package zw.gov.mohcc.impilo.offlinesync.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.offlinesync.domain.ConflictResolution;

/**
 * Request DTO for resolving a conflict in the conflict queue.
 */
public class ResolveConflictRequest {

    @NotNull
    private ConflictResolution resolution;

    private String mergedPayload;

    // Getters and setters

    public ConflictResolution getResolution() { return resolution; }
    public void setResolution(ConflictResolution resolution) { this.resolution = resolution; }

    public String getMergedPayload() { return mergedPayload; }
    public void setMergedPayload(String mergedPayload) { this.mergedPayload = mergedPayload; }
}
