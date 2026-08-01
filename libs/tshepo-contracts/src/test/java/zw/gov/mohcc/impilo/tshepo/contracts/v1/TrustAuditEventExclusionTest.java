package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checkpoint-2 closure: TrustAuditEvent must reject or omit raw access/refresh tokens,
 * passwords, OTP and recovery codes, private keys, and unnecessary clinical payloads.
 */
class TrustAuditEventExclusionTest {

    private static final Instant TS = Instant.parse("2026-08-01T09:15:00Z");

    private static TrustAuditEvent withAttributes(Map<String, String> attributes) {
        return new TrustAuditEvent(
                TrustContractVersion.V1, "evt-1", TS,
                "req-1", "trace-1", "dec-1", "experience-bff", "pct-service",
                "HID-PRV-004410", "wct-1", null, "TREATMENT", "OPEN_ENCOUNTER",
                "Encounter/enc-1", "bff_session", 2, 2,
                RecoveryAuthenticationState.NONE, "policy-1", "tshepo-authz-service",
                "ALLOW", attributes);
    }

    @Test
    @DisplayName("rejects raw access tokens in attribute values")
    void rejectsAccessToken() {
        assertThatThrownBy(() -> withAttributes(Map.of(
                "token", "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJsZWFrIn0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withAttributes(Map.of("k", "Bearer abc.def.ghi")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects refresh tokens by key or value")
    void rejectsRefreshToken() {
        assertThatThrownBy(() -> withAttributes(Map.of("refresh_token", "opaque-value")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withAttributes(Map.of("k", "refresh_token=abc123")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects passwords")
    void rejectsPassword() {
        assertThatThrownBy(() -> withAttributes(Map.of("password", "hunter2")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withAttributes(Map.of("k", "password=hunter2")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects OTP and recovery codes")
    void rejectsOtpAndRecoveryCodes() {
        assertThatThrownBy(() -> withAttributes(Map.of("otp", "123456")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withAttributes(Map.of("k", "recovery-code: ABCD-EFGH")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withAttributes(Map.of("recovery_code", "ABCD-EFGH")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects private key material")
    void rejectsPrivateKeys() {
        assertThatThrownBy(() -> withAttributes(Map.of(
                "dump", "-----BEGIN PRIVATE KEY----- MIIEvQ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withAttributes(Map.of("private_key", "raw")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects clinical payload keys (clinical, observation, fhir, diagnosis, medication)")
    void rejectsClinicalPayloads() {
        for (String key : new String[]{
                "clinicalNote", "observationBundle", "fhirResource", "diagnosisText", "medicationList"}) {
            assertThatThrownBy(() -> withAttributes(Map.of(key, "v")))
                    .as("clinical payload key %s must be rejected", key)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("rejects secrets in identifier fields, not only attributes")
    void rejectsSecretsInIdentifierFields() {
        assertThatThrownBy(() -> new TrustAuditEvent(
                TrustContractVersion.V1, "evt-1", TS,
                "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0", null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("omits null fields on the wire and keeps safe operational attributes")
    void omitsNullsAndKeepsSafeAttributes() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TrustAuditEvent event = new TrustAuditEvent(
                TrustContractVersion.V1, "evt-2", TS,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                Map.of("cohort", "preview"));
        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"eventId\":\"evt-2\"").contains("\"cohort\":\"preview\"");
        assertThat(json).doesNotContain("requestId").doesNotContain("null");
    }
}
