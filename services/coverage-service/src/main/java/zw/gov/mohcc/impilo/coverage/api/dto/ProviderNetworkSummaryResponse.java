package zw.gov.mohcc.impilo.coverage.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import zw.gov.mohcc.impilo.coverage.domain.ProviderNetworkEntity;

public record ProviderNetworkSummaryResponse(
        UUID networkId,
        String name,
        String payerId,
        String networkType,
        String status,
        long memberCount,
        OffsetDateTime createdAt) {

    public static ProviderNetworkSummaryResponse of(ProviderNetworkEntity n, long memberCount) {
        return new ProviderNetworkSummaryResponse(
                n.getNetworkId(),
                n.getName(),
                n.getPayerId(),
                n.getNetworkType(),
                n.getStatus(),
                memberCount,
                n.getCreatedAt());
    }
}
