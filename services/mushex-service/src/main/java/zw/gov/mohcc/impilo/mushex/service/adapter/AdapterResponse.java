package zw.gov.mohcc.impilo.mushex.service.adapter;

import java.util.Map;

/**
 * Immutable response from a {@link PaymentRailAdapter} call.
 *
 * @param adapterRef external reference from the payment provider
 * @param status     normalized status: PENDING, SUCCESS, or FAILED
 * @param message    human-readable message or error description
 * @param metadata   provider-specific metadata (transaction ID, timestamps, etc.)
 */
public record AdapterResponse(
        String adapterRef,
        String status,
        String message,
        Map<String, Object> metadata
) {}
