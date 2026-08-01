package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;
import zw.gov.mohcc.impilo.experience.service.ContactOtpService;
import zw.gov.mohcc.impilo.experience.service.ContactOtpService.Contact;
import zw.gov.mohcc.impilo.experience.service.ContactOtpService.VerifyOutcome;
import zw.gov.mohcc.impilo.experience.service.ContactOtpService.VerifyResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthContactOtpControllerTest {

    private static final Contact PHONE_CONTACT =
            new Contact("PHONE", "+263771234567", "+263*******67");
    private static final Contact EMAIL_CONTACT =
            new Contact("EMAIL", "person@example.test", "p***@example.test");

    @Mock private ContactOtpService contactOtpService;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private IdentityAssuranceServiceClient identityAssuranceClient;
    @Mock private ObjectProvider<JwtDecoder> jwtDecoderProvider;
    @Mock private JwtDecoder jwtDecoder;
    @Mock private HttpServletRequest servletRequest;

    private AuthContactOtpController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthContactOtpController(contactOtpService, keycloakAdminClient,
                identityAssuranceClient, jwtDecoderProvider);
        when(servletRequest.getRemoteAddr()).thenReturn("10.0.0.9");
        when(jwtDecoderProvider.getIfAvailable()).thenReturn(jwtDecoder);
    }

    private Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Nested
    @DisplayName("POST /internal/v1/auth/contact/otp/request")
    class Request {

        @Test
        void issuesOtpAndReturns202WithMaskedValueOnly() {
            when(contactOtpService.requestOtp(any(), eq("0771234567"), anyString()))
                    .thenReturn(PHONE_CONTACT);

            ResponseEntity<Map<String, Object>> response = controller.requestOtp(
                    "t1", "pod", "req", "corr",
                    body("channel", "PHONE", "value", "0771234567"), servletRequest);

            assertThat(response.getStatusCode().value()).isEqualTo(202);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
            assertThat(attrs.get("status")).isEqualTo("SENT");
            assertThat(attrs.get("maskedValue")).isEqualTo("+263*******67");
            assertThat(response.getBody().toString()).doesNotContain("+263771234567");
        }

        @Test
        void rateLimitedIs429WithRetryAfter() {
            when(contactOtpService.requestOtp(any(), any(), any()))
                    .thenThrow(new ContactOtpService.OtpRateLimitedException("slow down", 42));

            ResponseEntity<Map<String, Object>> response = controller.requestOtp(
                    "t1", "pod", "req", "corr", body("value", "0771234567"), servletRequest);

            assertThat(response.getStatusCode().value()).isEqualTo(429);
            assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
        }

        @Test
        void deliveryUnavailableIs503_failClosed() {
            when(contactOtpService.requestOtp(any(), any(), any()))
                    .thenThrow(new ContactOtpService.OtpUnavailableException("down", null));

            assertThat(controller.requestOtp("t1", "pod", "req", "corr",
                    body("value", "0771234567"), servletRequest).getStatusCode().value())
                    .isEqualTo(503);
        }

        @Test
        void invalidContactIs400() {
            when(contactOtpService.requestOtp(any(), any(), any()))
                    .thenThrow(new ContactOtpService.InvalidContactException("Invalid phone number"));

            assertThat(controller.requestOtp("t1", "pod", "req", "corr",
                    body("value", "xxx"), servletRequest).getStatusCode().value())
                    .isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("POST /internal/v1/auth/contact/otp/verify — OTP outcomes")
    class VerifyOutcomes {

        @Test
        void invalidCodeIs401() {
            when(contactOtpService.verifyOtp(any(), any()))
                    .thenReturn(new VerifyResult(VerifyOutcome.INVALID_CODE, PHONE_CONTACT));
            assertThat(verifyCall(body("value", "0771234567", "code", "000000",
                    "purpose", "REGISTER")).getStatusCode().value()).isEqualTo(401);
        }

        @Test
        void expiredIs400() {
            when(contactOtpService.verifyOtp(any(), any()))
                    .thenReturn(new VerifyResult(VerifyOutcome.EXPIRED_OR_UNKNOWN, PHONE_CONTACT));
            assertThat(verifyCall(body("value", "0771234567", "code", "123456",
                    "purpose", "REGISTER")).getStatusCode().value()).isEqualTo(400);
        }

        @Test
        void lockedIs429() {
            when(contactOtpService.verifyOtp(any(), any()))
                    .thenReturn(new VerifyResult(VerifyOutcome.LOCKED, PHONE_CONTACT));
            assertThat(verifyCall(body("value", "0771234567", "code", "123456",
                    "purpose", "ATTACH")).getStatusCode().value()).isEqualTo(429);
        }

        @Test
        void unknownPurposeIs400_beforeAnyOtpCheck() {
            assertThat(verifyCall(body("value", "0771234567", "code", "123456",
                    "purpose", "LOGIN")).getStatusCode().value()).isEqualTo(400);
            verify(contactOtpService, never()).verifyOtp(any(), any());
        }

        private ResponseEntity<Map<String, Object>> verifyCall(Map<String, Object> b) {
            return controller.verifyOtp("t1", "pod", "req", "corr", null, b);
        }
    }

    @Nested
    @DisplayName("purpose=REGISTER")
    class Register {

        @BeforeEach
        void otpPasses() {
            when(contactOtpService.verifyOtp(any(), any()))
                    .thenReturn(new VerifyResult(VerifyOutcome.VERIFIED, EMAIL_CONTACT));
            when(keycloakAdminClient.createContactUser(any()))
                    .thenReturn(KeycloakAdminClient.KeycloakUserResult.created("kc-user-1"));
            when(keycloakAdminClient.serviceAccountBearer()).thenReturn("svc-token");
            when(identityAssuranceClient.recordContactVerified(any(), any(), any(), any()))
                    .thenReturn(new ObjectMapper().createObjectNode());
            when(keycloakAdminClient.sendExecuteActionsEmail(
                    eq("kc-user-1"), eq(java.util.List.of("UPDATE_PASSWORD")), eq(1800)))
                    .thenReturn(true);
        }

        @Test
        void createsCitizenUserRecordsAttestationAndSendsNativeActivation() {
            ResponseEntity<Map<String, Object>> response = controller.verifyOtp(
                    "t1", "pod", "req", "corr", null,
                    body("value", "person@example.test", "code", "123456", "purpose", "REGISTER"));

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            verify(keycloakAdminClient).createContactUser(any());
            verify(identityAssuranceClient).recordContactVerified(
                    eq("kc-user-1"), eq("EMAIL"), eq("p***@example.test"), any());
            verify(keycloakAdminClient).sendExecuteActionsEmail(
                    "kc-user-1", java.util.List.of("UPDATE_PASSWORD"), 1800);
        }

        @Test
        void registrationNeverReturnsBrowserTokenOrRefreshCookie() {

            ResponseEntity<Map<String, Object>> response = controller.verifyOtp(
                    "t1", "pod", "req", "corr", null,
                    body("value", "person@example.test", "code", "123456", "purpose", "REGISTER"));

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            assertThat(data.get("type")).isEqualTo("registration");
            assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
            assertThat(response.getBody().toString()).doesNotContain("access_token", "refresh_token");
        }

        @Test
        void phoneVerificationCannotBootstrapAnAuthenticationCredential() {
            when(contactOtpService.verifyOtp(any(), any()))
                    .thenReturn(new VerifyResult(VerifyOutcome.VERIFIED, PHONE_CONTACT));
            ResponseEntity<Map<String, Object>> response = controller.verifyOtp(
                    "t1", "pod", "req", "corr", null,
                    body("value", "0771234567", "code", "123456", "purpose", "REGISTER"));
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            verify(keycloakAdminClient, never()).createContactUser(any());
        }

        @Test
        void existingUserIs409() {
            when(keycloakAdminClient.createContactUser(any())).thenReturn(
                    KeycloakAdminClient.KeycloakUserResult.failed("USER_EXISTS", "exists"));
            assertThat(controller.verifyOtp("t1", "pod", "req", "corr", null,
                    body("value", "0771234567", "code", "123456", "purpose", "REGISTER",
                            "email", "person@example.test")).getStatusCode().value()).isEqualTo(409);
        }

        @Test
        void attestationFailureRollsBackUserAnd503() {
            when(identityAssuranceClient.recordContactVerified(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("ia down"));

            ResponseEntity<Map<String, Object>> response = controller.verifyOtp(
                    "t1", "pod", "req", "corr", null,
                    body("value", "person@example.test", "code", "123456", "purpose", "REGISTER"));

            assertThat(response.getStatusCode().value()).isEqualTo(503);
            verify(keycloakAdminClient).deleteUser("kc-user-1");
        }
    }

    @Nested
    @DisplayName("purpose=ATTACH")
    class Attach {

        @BeforeEach
        void otpPasses() {
            when(contactOtpService.verifyOtp(any(), any()))
                    .thenReturn(new VerifyResult(VerifyOutcome.VERIFIED, PHONE_CONTACT));
        }

        @Test
        void recordsAttestationForJwtSubject() {
            Jwt jwt = mock(Jwt.class);
            when(jwt.getSubject()).thenReturn("kc-user-9");
            when(jwtDecoder.decode("user-token")).thenReturn(jwt);
            when(identityAssuranceClient.recordContactVerified(any(), any(), any(), any()))
                    .thenReturn(new ObjectMapper().createObjectNode());

            ResponseEntity<Map<String, Object>> response = controller.verifyOtp(
                    "t1", "pod", "req", "corr", "Bearer user-token",
                    body("value", "0771234567", "code", "123456", "purpose", "ATTACH"));

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(identityAssuranceClient).recordContactVerified(
                    eq("kc-user-9"), eq("PHONE"), eq("+263*******67"), any());
            verify(keycloakAdminClient, never()).createContactUser(any());
        }

        @Test
        void missingBearerIs401() {
            assertThat(controller.verifyOtp("t1", "pod", "req", "corr", null,
                    body("value", "0771234567", "code", "123456", "purpose", "ATTACH"))
                    .getStatusCode().value()).isEqualTo(401);
        }

        @Test
        void invalidTokenIs401() {
            when(jwtDecoder.decode(anyString())).thenThrow(new RuntimeException("bad token"));
            assertThat(controller.verifyOtp("t1", "pod", "req", "corr", "Bearer nope",
                    body("value", "0771234567", "code", "123456", "purpose", "ATTACH"))
                    .getStatusCode().value()).isEqualTo(401);
        }
    }
}
