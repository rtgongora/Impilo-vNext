package zw.gov.mohcc.impilo.msikaflow.api.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ValidateCartRequest(
        @NotEmpty List<CartItemDto> items,
        String channel
) {
    public record CartItemDto(String msikaCoreCode, int qty) {}
}
