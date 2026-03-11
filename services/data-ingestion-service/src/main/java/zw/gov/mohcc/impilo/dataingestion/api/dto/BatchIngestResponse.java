package zw.gov.mohcc.impilo.dataingestion.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BatchIngestResponse(
        int accepted,
        int replayed,
        int rejected,
        List<BatchItemResult> results
) {

    public record BatchItemResult(
            int index,
            String status,
            @JsonProperty("receipt_id") String receiptId,
            @JsonProperty("event_id") String eventId,
            String error
    ) {
        public static BatchItemResult accepted(int index, String receiptId, String eventId) {
            return new BatchItemResult(index, "ACCEPTED", receiptId, eventId, null);
        }

        public static BatchItemResult replayed(int index, String receiptId, String eventId) {
            return new BatchItemResult(index, "REPLAYED", receiptId, eventId, null);
        }

        public static BatchItemResult rejected(int index, String eventId, String error) {
            return new BatchItemResult(index, "REJECTED", null, eventId, error);
        }
    }
}
