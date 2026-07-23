package zw.gov.mohcc.impilo.nhume.core;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.nhume.api.dto.ProofRequest;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.domain.HandoverVerificationGrade;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OF-B18 §12.4 — proof-of-handover grade-ladder decision matrix:
 * required-grade derivation per order class, achieved-grade computation,
 * named-recipient fail-closed enforcement and the controlled second factor.
 */
class HandoverGradePolicyTest {

    private static DeliveryRequestEntity delivery() {
        DeliveryRequestEntity d = new DeliveryRequestEntity(
                UUID.randomUUID(), UUID.randomUUID(), "NHM-T", "TEST");
        d.setRecipientName("Tariro Moyo");
        return d;
    }

    private static ProofRequest proof(String otp, String signatureUri, String receiverName,
                                      String idType, String idRef) {
        return new ProofRequest("DELIVERY", "OTP", "courier-1", otp, signatureUri, null, null,
                null, null, null, Map.of(), false, null, null, null,
                receiverName, idType, idRef);
    }

    // ── required-grade derivation ────────────────────────────────────────

    @Test
    void requiredGrade_defaultsBySeverity() {
        DeliveryRequestEntity plain = delivery();
        assertThat(HandoverGradePolicy.requiredGrade(plain))
                .isEqualTo(HandoverVerificationGrade.SIGNATURE_ONLY);

        DeliveryRequestEntity named = delivery();
        named.setNamedRecipientRequired(true);
        assertThat(HandoverGradePolicy.requiredGrade(named))
                .isEqualTo(HandoverVerificationGrade.NAMED_RECIPIENT_MATCH);

        DeliveryRequestEntity controlled = delivery();
        controlled.setControlledItem(true);
        assertThat(HandoverGradePolicy.requiredGrade(controlled))
                .isEqualTo(HandoverVerificationGrade.OTP_PLUS_ID);
    }

    @Test
    void requiredGrade_explicitDemandWins_butNeverBelowControlledFloor() {
        DeliveryRequestEntity d = delivery();
        d.setRequiredPodGrade("OTP_MATCH");
        assertThat(HandoverGradePolicy.requiredGrade(d)).isEqualTo(HandoverVerificationGrade.OTP_MATCH);

        // A weaker explicit demand can never undercut the §13.4 controlled floor.
        d.setControlledItem(true);
        d.setRequiredPodGrade("SIGNATURE_ONLY");
        assertThat(HandoverGradePolicy.requiredGrade(d)).isEqualTo(HandoverVerificationGrade.OTP_PLUS_ID);
    }

    @Test
    void ungradedLegacyDeliveries_neverRefused() {
        DeliveryRequestEntity d = delivery(); // no grade contract at all
        HandoverGradePolicy.Verdict v = HandoverGradePolicy.evaluate(
                d, proof(null, null, null, null, null), false);
        assertThat(v.pass()).isTrue();
        assertThat(v.achievedGradeName()).isEqualTo(HandoverGradePolicy.UNGRADED);
    }

    // ── achieved-grade + OTP fail-closed ─────────────────────────────────

    @Test
    void otpMatch_requiresServerHeldOtp_failClosedWhenAbsent() {
        DeliveryRequestEntity d = delivery();
        d.setRequiredPodGrade("OTP_MATCH");
        // No stored OTP: the presented code can never match — refused.
        HandoverGradePolicy.Verdict noTruth = HandoverGradePolicy.evaluate(
                d, proof("123456", null, null, null, null), false);
        assertThat(noTruth.pass()).isFalse();
        assertThat(noTruth.failureReason()).isEqualTo(HandoverGradePolicy.FAIL_GRADE_BELOW_REQUIRED);

        d.setHandoverOtp("654321");
        HandoverGradePolicy.Verdict wrong = HandoverGradePolicy.evaluate(
                d, proof("123456", null, null, null, null), false);
        assertThat(wrong.pass()).isFalse();

        HandoverGradePolicy.Verdict right = HandoverGradePolicy.evaluate(
                d, proof("654321", null, null, null, null), false);
        assertThat(right.pass()).isTrue();
        assertThat(right.achievedGrade()).isEqualTo(HandoverVerificationGrade.OTP_MATCH);
    }

    @Test
    void signatureOnlyMeetsTheLowestRung() {
        DeliveryRequestEntity d = delivery();
        d.setRequiredPodGrade("SIGNATURE_ONLY");
        HandoverGradePolicy.Verdict v = HandoverGradePolicy.evaluate(
                d, proof(null, "sig:blob", null, null, null), false);
        assertThat(v.pass()).isTrue();
        assertThat(v.achievedGrade()).isEqualTo(HandoverVerificationGrade.SIGNATURE_ONLY);
    }

    // ── named-recipient enforcement (fail-closed) ────────────────────────

    @Test
    void namedRecipient_mismatchFailsClosed_evenWithStrongerFactors() {
        DeliveryRequestEntity d = delivery();
        d.setNamedRecipientRequired(true);
        d.setHandoverOtp("111111");
        // OTP matches AND ID checked — but the receiver is not the named recipient.
        HandoverGradePolicy.Verdict v = HandoverGradePolicy.evaluate(
                d, proof("111111", null, "Someone Else", "NATIONAL_ID", "63-123456A00"), false);
        assertThat(v.pass()).isFalse();
        assertThat(v.failureReason()).isEqualTo(HandoverGradePolicy.FAIL_NAMED_RECIPIENT_MISMATCH);
        assertThat(v.namedRecipientMatch()).isFalse();
    }

    @Test
    void namedRecipient_matchIsNormalizedNotExact() {
        DeliveryRequestEntity d = delivery();
        d.setNamedRecipientRequired(true);
        HandoverGradePolicy.Verdict v = HandoverGradePolicy.evaluate(
                d, proof(null, null, "  TARIRO   moyo ", null, null), false);
        assertThat(v.pass()).isTrue();
        assertThat(v.namedRecipientMatch()).isTrue();
        assertThat(v.achievedGrade()).isEqualTo(HandoverVerificationGrade.NAMED_RECIPIENT_MATCH);
    }

    @Test
    void namedRecipient_missingReceiverNameFailsClosed() {
        DeliveryRequestEntity d = delivery();
        d.setNamedRecipientRequired(true);
        HandoverGradePolicy.Verdict v = HandoverGradePolicy.evaluate(
                d, proof(null, "sig:blob", null, null, null), false);
        assertThat(v.pass()).isFalse();
        assertThat(v.failureReason()).isEqualTo(HandoverGradePolicy.FAIL_NAMED_RECIPIENT_MISMATCH);
    }

    // ── controlled second factor (§13.4) ─────────────────────────────────

    @Test
    void controlled_requiresOtpPlusIdCheck_refusesAnythingLess() {
        DeliveryRequestEntity d = delivery();
        d.setControlledItem(true);
        d.setHandoverOtp("222222");

        // OTP alone — refused.
        HandoverGradePolicy.Verdict otpOnly = HandoverGradePolicy.evaluate(
                d, proof("222222", null, "Tariro Moyo", null, null), false);
        assertThat(otpOnly.pass()).isFalse();
        assertThat(otpOnly.failureReason())
                .isEqualTo(HandoverGradePolicy.FAIL_CONTROLLED_SECOND_FACTOR_MISSING);

        // ID alone — refused.
        HandoverGradePolicy.Verdict idOnly = HandoverGradePolicy.evaluate(
                d, proof(null, null, "Tariro Moyo", "NATIONAL_ID", "63-123456A00"), false);
        assertThat(idOnly.pass()).isFalse();

        // OTP + ID — passes at the OTP_PLUS_ID grade.
        HandoverGradePolicy.Verdict both = HandoverGradePolicy.evaluate(
                d, proof("222222", null, "Tariro Moyo", "NATIONAL_ID", "63-123456A00"), false);
        assertThat(both.pass()).isTrue();
        assertThat(both.achievedGrade()).isEqualTo(HandoverVerificationGrade.OTP_PLUS_ID);
    }

    @Test
    void controlled_verifiedBiometricSubstitutesForOtp_notForIdCheck() {
        DeliveryRequestEntity d = delivery();
        d.setControlledItem(true); // no stored OTP at all

        HandoverGradePolicy.Verdict bioPlusId = HandoverGradePolicy.evaluate(
                d, proof(null, null, "Tariro Moyo", "NATIONAL_ID", "63-123456A00"), true);
        assertThat(bioPlusId.pass()).isTrue();
        assertThat(bioPlusId.achievedGrade()).isEqualTo(HandoverVerificationGrade.OTP_PLUS_ID);

        HandoverGradePolicy.Verdict bioAlone = HandoverGradePolicy.evaluate(
                d, proof(null, null, "Tariro Moyo", null, null), true);
        assertThat(bioAlone.pass()).isFalse();
        assertThat(bioAlone.failureReason())
                .isEqualTo(HandoverGradePolicy.FAIL_CONTROLLED_SECOND_FACTOR_MISSING);
    }

    @Test
    void gpsIsNeverConsulted() {
        // CC-8: a geofence hit with nothing else achieves NO grade on a graded class.
        DeliveryRequestEntity d = delivery();
        d.setRequiredPodGrade("SIGNATURE_ONLY");
        ProofRequest gpsOnly = new ProofRequest("DELIVERY", "GPS", "courier-1", null, null, null,
                null, true, null, null, Map.of(), false, null, null, null, null, null, null);
        HandoverGradePolicy.Verdict v = HandoverGradePolicy.evaluate(d, gpsOnly, false);
        assertThat(v.pass()).isFalse();
        assertThat(v.failureReason()).isEqualTo(HandoverGradePolicy.FAIL_GRADE_BELOW_REQUIRED);
    }
}
