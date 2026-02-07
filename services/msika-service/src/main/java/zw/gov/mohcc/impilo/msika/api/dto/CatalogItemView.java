package zw.gov.mohcc.impilo.msika.api.dto;

import java.time.OffsetDateTime;

public record CatalogItemView(
    String itemId,
    String catalogId,
    String kind,
    String canonicalCode,
    String displayName,
    String description,
    String[] synonyms,
    String[] tags,
    Object restrictions,
    Object ziboBindings,
    Object metadata,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int lockVersion,
    ProductDetailDto product,
    ServiceDetailDto service,
    OrderableDetailDto orderable,
    ChargeableDetailDto chargeable
) {}
