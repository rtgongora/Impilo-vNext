package zw.gov.mohcc.impilo.pct.api.dto;

import java.util.UUID;

/**
 * B5: check in a patient by presenting their SMART card (identity function), routed
 * through the shared card-resolve seam. Exactly one of {@code cardNumber} /
 * {@code qrToken} / {@code cardAssertion} identifies the card; the resolved Health
 * ID (== CPID) becomes the journey's patient — the caller never supplies the CPID,
 * so one person's card can't start another's journey.
 */
public record CheckInByCardRequest(
        String cardNumber,
        String qrToken,
        String cardAssertion,
        UUID facilityId,
        String referralSource,
        String referralId,
        UUID appointmentId
) {}
