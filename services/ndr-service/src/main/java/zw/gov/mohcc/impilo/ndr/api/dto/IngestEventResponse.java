package zw.gov.mohcc.impilo.ndr.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IngestEventResponse(
        @JsonProperty("receipt_id") String receiptId,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("stored_at") String storedAt,
        String dedupe
) {}
