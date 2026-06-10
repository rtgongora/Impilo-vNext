package zw.gov.mohcc.impilo.msikaflow.api.dto;

import java.util.List;

public record PagedOrderView(List<OrderView> items, int page, int size, long total_elements) {}
