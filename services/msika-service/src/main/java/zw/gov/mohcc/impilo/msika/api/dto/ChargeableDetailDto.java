package zw.gov.mohcc.impilo.msika.api.dto;

public record ChargeableDetailDto(
    String targetKind,
    String targetItemId,
    String tariffCode,
    Object pricingRefs,
    String costMethodRef,
    Object billableRules
) {}
