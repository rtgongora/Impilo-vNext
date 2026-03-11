package zw.gov.mohcc.impilo.dataingestion.api.dto;

import java.util.List;

public record BatchIngestRequest(
        List<IngestEventRequest> items
) {}
