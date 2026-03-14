package zw.gov.mohcc.impilo.iotingestion.api.dto;

import java.util.List;
import java.util.UUID;

public record BatchIngestResponse(
        int accepted,
        int rejected,
        List<UUID> readingIds) {
}
