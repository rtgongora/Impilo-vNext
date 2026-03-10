package zw.gov.mohcc.impilo.indawo.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record CreateAddressRequest(
        @NotBlank String addressType,
        @NotBlank String line1,
        String line2,
        String suburb,
        String city,
        String district,
        @NotBlank String province,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude
) {}
