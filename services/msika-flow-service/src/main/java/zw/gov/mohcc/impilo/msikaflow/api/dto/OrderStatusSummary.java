package zw.gov.mohcc.impilo.msikaflow.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lightweight order status summary for cross-service gating and dashboards.
 */
public record OrderStatusSummary(
        String orderId,
        UUID tenantId,
        String status,
        String orderType,
        BigDecimal amountTotal,
        String currency,
        String facilityRef,
        String vendorRef,
        String providerRef
) {}

