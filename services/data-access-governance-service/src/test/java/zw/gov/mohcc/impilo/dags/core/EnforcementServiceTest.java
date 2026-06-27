package zw.gov.mohcc.impilo.dags.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.dags.core.EnforcementService.PermitContext;
import zw.gov.mohcc.impilo.dags.core.EnforcementService.Reason;
import zw.gov.mohcc.impilo.dags.persistence.entity.AccessRequestEntity;
import zw.gov.mohcc.impilo.dags.persistence.repository.PermitReplayRepository;

/**
 * Unit-proves the B3 permit engine with REAL HMAC-SHA256: full-context binding on issue,
 * and verifyAndConsume's constant-time signature check + expiry + every bound-claim check
 * + replay rejection. The replay store is mocked here; the persistence/replay path is
 * runtime-proven against a real Postgres by PermitEnforcementRuntimeProofIT.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnforcementServiceTest {

    private static final String KEY = "test-signing-key-strong-enough";

    @Mock private PermitReplayRepository replayRepository;
    private EnforcementService enforcementService;

    private UUID tenantId;
    private AccessRequestEntity request;

    @BeforeEach
    void setUp() {
        enforcementService = new EnforcementService(KEY, 3600, replayRepository);
        lenient().when(replayRepository.existsByNonce(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        tenantId = UUID.randomUUID();
        request = new AccessRequestEntity();
        request.setId(101L);
        request.setTenantId(tenantId);
        request.setRequesterId("dr-jones");
        request.setResourceType("PATIENT_RECORD");
        request.setResourceId("patient-42");
        request.setPurpose("TREATMENT");
    }

    /** The context that exactly matches the issued token. */
    private PermitContext matchingContext() {
        return new PermitContext(tenantId, "dr-jones", "PATIENT_RECORD", "patient-42", "TREATMENT");
    }

    // ---- issuance --------------------------------------------------------

    @Test
    @DisplayName("issues a v2 token with a signature segment")
    void issuesV2Token() {
        String token = enforcementService.issuePermitToken(request);
        assertThat(token).startsWith("permit-token:v2:").contains(".");
    }

    @Test
    @DisplayName("each issued token is unique (fresh nonce)")
    void tokensAreUnique() {
        assertThat(enforcementService.issuePermitToken(request))
                .isNotEqualTo(enforcementService.issuePermitToken(request));
    }

    // ---- fail-closed key -------------------------------------------------

    @Test
    @DisplayName("refuses to start with the insecure default key")
    void failsClosedOnInsecureDefaultKey() {
        assertThatThrownBy(() -> new EnforcementService("change-me-in-prod", 3600, replayRepository))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("refuses to start with a blank or null key")
    void failsClosedOnBlankKey() {
        assertThatThrownBy(() -> new EnforcementService("", 3600, replayRepository)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EnforcementService("   ", 3600, replayRepository)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EnforcementService(null, 3600, replayRepository)).isInstanceOf(IllegalStateException.class);
    }

    // ---- verifyAndConsume: happy path ------------------------------------

    @Test
    @DisplayName("valid token + matching context verifies and consumes the nonce")
    void validToken_verifies() {
        String token = enforcementService.issuePermitToken(request);
        EnforcementService.PermitVerification r = enforcementService.verifyAndConsume(token, matchingContext());
        assertThat(r.valid()).isTrue();
        assertThat(r.reason()).isEqualTo(Reason.VALID);
        org.mockito.Mockito.verify(replayRepository).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    // ---- verifyAndConsume: fail-closed reasons ---------------------------

    @Test
    @DisplayName("tampered signature fails: INVALID_SIGNATURE")
    void tamperedSignature_failsClosed() {
        String token = enforcementService.issuePermitToken(request);
        // Flip the last character of the signature segment.
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertThat(enforcementService.verifyAndConsume(tampered, matchingContext()).reason())
                .isEqualTo(Reason.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("expired token fails: EXPIRED")
    void expiredToken_failsClosed() {
        String expired = craftToken(tenantId, "dr-jones", 101L, "PATIENT_RECORD", "patient-42",
                "TREATMENT", System.currentTimeMillis() / 1000 - 10 /* exp in the past */);
        assertThat(enforcementService.verifyAndConsume(expired, matchingContext()).reason())
                .isEqualTo(Reason.EXPIRED);
    }

    @Test
    @DisplayName("wrong tenant fails: WRONG_TENANT")
    void wrongTenant_failsClosed() {
        String token = enforcementService.issuePermitToken(request);
        PermitContext other = new PermitContext(UUID.randomUUID(), "dr-jones", "PATIENT_RECORD", "patient-42", "TREATMENT");
        assertThat(enforcementService.verifyAndConsume(token, other).reason()).isEqualTo(Reason.WRONG_TENANT);
    }

    @Test
    @DisplayName("wrong requester fails: WRONG_REQUESTER")
    void wrongRequester_failsClosed() {
        String token = enforcementService.issuePermitToken(request);
        PermitContext other = new PermitContext(tenantId, "dr-evil", "PATIENT_RECORD", "patient-42", "TREATMENT");
        assertThat(enforcementService.verifyAndConsume(token, other).reason()).isEqualTo(Reason.WRONG_REQUESTER);
    }

    @Test
    @DisplayName("wrong resource fails: WRONG_RESOURCE")
    void wrongResource_failsClosed() {
        String token = enforcementService.issuePermitToken(request);
        PermitContext other = new PermitContext(tenantId, "dr-jones", "PATIENT_RECORD", "patient-99", "TREATMENT");
        assertThat(enforcementService.verifyAndConsume(token, other).reason()).isEqualTo(Reason.WRONG_RESOURCE);
    }

    @Test
    @DisplayName("wrong purpose fails: WRONG_PURPOSE")
    void wrongPurpose_failsClosed() {
        String token = enforcementService.issuePermitToken(request);
        PermitContext other = new PermitContext(tenantId, "dr-jones", "PATIENT_RECORD", "patient-42", "RESEARCH");
        assertThat(enforcementService.verifyAndConsume(token, other).reason()).isEqualTo(Reason.WRONG_PURPOSE);
    }

    @Test
    @DisplayName("already-consumed nonce fails: REPLAYED")
    void replayedToken_failsClosed() {
        when(replayRepository.existsByNonce(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        String token = enforcementService.issuePermitToken(request);
        assertThat(enforcementService.verifyAndConsume(token, matchingContext()).reason()).isEqualTo(Reason.REPLAYED);
    }

    @Test
    @DisplayName("malformed / wrong-version tokens fail closed")
    void malformedAndWrongVersion_failClosed() {
        assertThat(enforcementService.verifyAndConsume("garbage", matchingContext()).reason()).isEqualTo(Reason.MALFORMED);
        assertThat(enforcementService.verifyAndConsume("permit-token:v2:nodot", matchingContext()).reason()).isEqualTo(Reason.MALFORMED);
        assertThat(enforcementService.verifyAndConsume("permit-token:v1:abc.def", matchingContext()).reason()).isEqualTo(Reason.UNSUPPORTED_VERSION);
    }

    // ---- helper: craft a correctly-signed token with an arbitrary expiry --

    private String craftToken(UUID tenant, String requester, long requestId, String resourceType,
                              String resourceId, String purpose, long exp) {
        long now = System.currentTimeMillis() / 1000;
        String payload = String.join("|",
                tenant.toString(),
                b64(sha256(requester)),
                String.valueOf(requestId),
                b64(resourceType.getBytes(StandardCharsets.UTF_8)),
                b64(resourceId.getBytes(StandardCharsets.UTF_8)),
                b64(purpose.getBytes(StandardCharsets.UTF_8)),
                String.valueOf(now),
                String.valueOf(exp),
                UUID.randomUUID().toString());
        String body = b64(payload.getBytes(StandardCharsets.UTF_8));
        return "permit-token:v2:" + body + "." + b64(hmac(body));
    }

    private static byte[] hmac(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] sha256(String v) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String b64(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
