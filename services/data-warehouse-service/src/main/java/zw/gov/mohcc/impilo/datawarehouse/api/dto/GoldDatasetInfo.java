package zw.gov.mohcc.impilo.datawarehouse.api.dto;

import java.util.List;

public record GoldDatasetInfo(String name, String displayName, String description, List<String> columns) {}
