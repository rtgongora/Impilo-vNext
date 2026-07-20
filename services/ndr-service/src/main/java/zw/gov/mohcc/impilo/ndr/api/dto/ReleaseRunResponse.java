package zw.gov.mohcc.impilo.ndr.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Summary of an NDR released-zone build. Carries counts, the terminal status and
 * the de-id manifest link only — never any released row values.
 */
public record ReleaseRunResponse(
        @JsonProperty("release_id") String releaseId,
        @JsonProperty("dataset_ref") String datasetRef,
        String status,
        @JsonProperty("deid_run_id") String deidRunId,
        @JsonProperty("manifest_id") String manifestId,
        @JsonProperty("policy_version") String policyVersion,
        @JsonProperty("rows_in") int rowsIn,
        @JsonProperty("rows_out") int rowsOut,
        @JsonProperty("rows_suppressed") int rowsSuppressed,
        @JsonProperty("rejection_reason") String rejectionReason,
        @JsonProperty("built_at") String builtAt
) {}
