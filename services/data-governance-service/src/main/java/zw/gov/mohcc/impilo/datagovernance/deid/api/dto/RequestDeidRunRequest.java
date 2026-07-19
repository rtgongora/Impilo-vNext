package zw.gov.mohcc.impilo.datagovernance.deid.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * De-identification run request. {@code source_rows} are CPID-keyed RAW-zone
 * rows pushed by the upstream batch (SHR / analytics pipeline extract); every
 * row must carry a {@code cpid} column, which never survives into the output.
 */
public record RequestDeidRunRequest(
        @NotBlank @JsonProperty("dataset_code") String datasetCode,
        @NotBlank @JsonProperty("requested_by") String requestedBy,
        @NotEmpty @JsonProperty("source_rows") List<Map<String, Object>> sourceRows
) {}
