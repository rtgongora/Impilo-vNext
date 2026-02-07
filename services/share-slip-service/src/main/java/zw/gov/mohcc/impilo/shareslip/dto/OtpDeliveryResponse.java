package zw.gov.mohcc.impilo.shareslip.dto;

import java.time.OffsetDateTime;

/**
 * Response indicating OTP delivery status.
 *
 * @param delivered  whether the OTP was successfully delivered
 * @param expiresAt  when the OTP expires
 * @param channel    the delivery channel (SMS or EMAIL)
 */
public record OtpDeliveryResponse(
        boolean delivered,
        OffsetDateTime expiresAt,
        String channel
) {
}
