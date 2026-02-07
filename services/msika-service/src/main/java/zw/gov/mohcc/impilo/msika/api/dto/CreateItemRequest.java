package zw.gov.mohcc.impilo.msika.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateItemRequest(
    @NotBlank String kind,
    @NotBlank String canonicalCode,
    @NotBlank String displayName,
    String description,
    String[] synonyms,
    String[] tags,
    Map<String, Object> restrictions,
    Object[] ziboBindings,
    Map<String, Object> metadata,
    ProductDetailDto product,
    ServiceDetailDto service,
    OrderableDetailDto orderable,
    ChargeableDetailDto chargeable
) {}
