package zw.gov.mohcc.impilo.nhume.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the client↔server payload contract for the sign-off lifecycle.
 *
 * Web and mobile clients send {@code method} (not {@code proof_method}) and
 * {@code mode} (not {@code mode_category}). Before the @JsonAlias fix these
 * bound to null — @NotBlank proof_method failed as a silent 400, so proof
 * capture (the collection / drop-off sign-off) never worked.
 */
class NhumePayloadBindingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void proofRequestBindsLegacyMethodAlias() throws Exception {
        String json = "{\"method\":\"OTP\",\"otp_code\":\"1234\",\"stage\":\"PICKUP\","
                + "\"mark_delivered\":false,\"evidence_ref\":\"sig-abc\"}";
        ProofRequest req = mapper.readValue(json, ProofRequest.class);

        assertEquals("OTP", req.proofMethod(), "legacy 'method' must bind to proof_method");
        assertEquals("PICKUP", req.proofStage(), "legacy 'stage' must bind to proof_stage");
        assertEquals("1234", req.otpCode());
        assertEquals("sig-abc", req.signatureUri(), "'evidence_ref' must bind to signature_uri");
        assertEquals(Boolean.FALSE, req.markDelivered());
    }

    @Test
    void proofRequestStillBindsCanonicalNames() throws Exception {
        String json = "{\"proof_method\":\"RECIPIENT_SIGNATURE\",\"proof_stage\":\"DELIVERY\",\"mark_delivered\":true}";
        ProofRequest req = mapper.readValue(json, ProofRequest.class);
        assertEquals("RECIPIENT_SIGNATURE", req.proofMethod());
        assertEquals("DELIVERY", req.proofStage());
        assertTrue(req.markDelivered());
    }

    @Test
    void assignRequestBindsLegacyModeAlias() throws Exception {
        String json = "{\"courier_id\":\"11111111-1111-1111-1111-111111111111\",\"mode\":\"BICYCLE\"}";
        AssignDeliveryRequest req = mapper.readValue(json, AssignDeliveryRequest.class);
        assertEquals("BICYCLE", req.modeCategory(), "legacy 'mode' must bind to mode_category");
    }
}
