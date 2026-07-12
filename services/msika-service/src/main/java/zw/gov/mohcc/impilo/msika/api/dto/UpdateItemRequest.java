package zw.gov.mohcc.impilo.msika.api.dto;

import java.util.Map;

public record UpdateItemRequest(
    String displayName,
    String description,
    String[] synonyms,
    String[] tags,
    String sourcingCategoryCode,
    Map<String, Object> restrictions,
    Object[] ziboBindings,
    Map<String, Object> metadata,
    int lockVersion,
    ProductDetailDto product,
    ServiceDetailDto service,
    OrderableDetailDto orderable,
    ChargeableDetailDto chargeable
) {}
