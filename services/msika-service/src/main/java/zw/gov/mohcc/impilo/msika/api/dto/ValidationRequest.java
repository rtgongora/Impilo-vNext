package zw.gov.mohcc.impilo.msika.api.dto;

public record ValidationRequest(
    String itemId,
    String kind,
    String canonicalCode,
    Object restrictions,
    Object[] ziboBindings
) {}
