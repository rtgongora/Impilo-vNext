package zw.gov.mohcc.impilo.datagovernance.deid.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DeidRunResponse(
        @JsonProperty("run_id") UUID runId,
        @JsonProperty("dataset_code") String datasetCode,
        String status,
        @JsonProperty("rows_in") int rowsIn,
        @JsonProperty("rows_out") int rowsOut,
        @JsonProperty("rows_suppressed") int rowsSuppressed,
        @JsonProperty("rejection_reason") String rejectionReason,
        @JsonProperty("policy_version") String policyVersion,
        @JsonProperty("qa_report") JsonNode qaReport,
        @JsonProperty("manifest_id") UUID manifestId,
        @JsonProperty("released_rows") List<Map<String, Object>> releasedRows,
        @JsonProperty("requested_at") String requestedAt,
        @JsonProperty("completed_at") String completedAt
) {}
