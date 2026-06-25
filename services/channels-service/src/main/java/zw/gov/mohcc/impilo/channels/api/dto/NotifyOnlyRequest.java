package zw.gov.mohcc.impilo.channels.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to send an external <b>notify-only</b> message: a content-free prompt over an external
 * channel (SMS/EMAIL/WHATSAPP) telling the recipient to log in to view something securely.
 *
 * <p>By design this carries <b>no message body</b> — the content is always a fixed, non-clinical
 * template — so clinical detail can never leak over an insecure channel. {@code reference} is an
 * opaque, non-clinical correlation token (e.g. a notification id), not subject data.</p>
 */
public record NotifyOnlyRequest(
        @NotBlank String channelType,
        @NotBlank String recipient,
        String reference
) {}
