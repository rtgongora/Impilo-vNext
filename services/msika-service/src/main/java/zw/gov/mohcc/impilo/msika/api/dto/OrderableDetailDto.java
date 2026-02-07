package zw.gov.mohcc.impilo.msika.api.dto;

public record OrderableDetailDto(
    String orderType,
    String targetKind,
    String targetItemId,
    String specimenType,
    String bodySite,
    String instructionsTemplate,
    Object criticalResultPolicy
) {}
