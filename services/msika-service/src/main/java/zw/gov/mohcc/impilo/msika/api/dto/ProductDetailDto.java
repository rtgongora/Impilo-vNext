package zw.gov.mohcc.impilo.msika.api.dto;

public record ProductDetailDto(
    String form,
    String strength,
    String route,
    String uom,
    Integer packSize,
    String barcode,
    Boolean batchTracked,
    Boolean expiryTracked,
    Boolean coldChain,
    Boolean controlled,
    String manufacturer,
    String atcCode
) {}
