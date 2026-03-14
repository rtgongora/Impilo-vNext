package zw.gov.mohcc.impilo.datawarehouse.api.dto;

import java.util.List;

public record GoldQueryResponse(String dataset, List<?> items, String nextCursor, long totalCount) {}
