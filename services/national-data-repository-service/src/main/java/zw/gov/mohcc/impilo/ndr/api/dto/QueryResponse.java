package zw.gov.mohcc.impilo.ndr.api.dto;

import java.util.List;

public record QueryResponse(
        String datasetKey,
        int totalRows,
        List<String> rows
) {}
