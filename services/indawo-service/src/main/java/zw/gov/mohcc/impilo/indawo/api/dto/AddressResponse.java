package zw.gov.mohcc.impilo.indawo.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        String addressType,
        String line1,
        String line2,
        String suburb,
        String city,
        String district,
        String province,
        String country,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String geocodeQuality,
        boolean verified
) {}
