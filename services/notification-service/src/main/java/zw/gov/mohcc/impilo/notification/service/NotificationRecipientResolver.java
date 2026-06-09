package zw.gov.mohcc.impilo.notification.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves synthetic scheduling addresses ({@code provider:}, {@code scheduling:facility:})
 * to concrete inbox recipients used for notification filtering and delivery.
 */
@Component
public class NotificationRecipientResolver {

    /**
     * @return one or more inbox recipient addresses; empty when the input cannot be resolved
     */
    public List<String> resolveInboxRecipients(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return List.of();
        }
        String trimmed = rawAddress.trim();
        if (trimmed.startsWith("provider:")) {
            String providerId = trimmed.substring("provider:".length()).trim();
            if (!providerId.isEmpty()) {
                return List.of("actor:" + providerId);
            }
        }
        if (trimmed.startsWith("scheduling:facility:")) {
            String facilityId = trimmed.substring("scheduling:facility:".length()).trim();
            if (!facilityId.isEmpty()) {
                return List.of("facility-scheduling:" + facilityId);
            }
        }
        if (trimmed.startsWith("ward:")) {
            return List.of(trimmed.toLowerCase(Locale.ROOT));
        }
        if (trimmed.startsWith("actor:") || trimmed.startsWith("staff:") || trimmed.startsWith("facility-scheduling:")) {
            return List.of(trimmed);
        }
        return List.of(trimmed);
    }

    public String primaryInboxRecipient(String rawAddress) {
        List<String> resolved = resolveInboxRecipients(rawAddress);
        return resolved.isEmpty() ? rawAddress : resolved.get(0);
    }

    public List<String> expandForDelivery(String rawAddress) {
        List<String> resolved = resolveInboxRecipients(rawAddress);
        if (!resolved.isEmpty()) {
            return resolved;
        }
        List<String> fallback = new ArrayList<>();
        fallback.add(rawAddress);
        return fallback;
    }
}
