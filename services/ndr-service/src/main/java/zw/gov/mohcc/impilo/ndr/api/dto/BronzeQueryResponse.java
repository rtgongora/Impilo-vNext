package zw.gov.mohcc.impilo.ndr.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BronzeQueryResponse(
        List<BronzeEventItem> items,
        String cursor,
        int limit,
        @JsonProperty("has_more") boolean hasMore
) {

    public record BronzeEventItem(
            @JsonProperty("receipt_id") String receiptId,
            @JsonProperty("event_id") String eventId,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("subject_type") String subjectType,
            @JsonProperty("subject_id") String subjectId,
            @JsonProperty("occurred_at") String occurredAt,
            @JsonProperty("stored_at") String storedAt,
            @JsonProperty("envelope_json") Object envelopeJson
    ) {}
}
