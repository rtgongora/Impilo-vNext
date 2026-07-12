package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.service.ContactOtpService.Contact;
import zw.gov.mohcc.impilo.experience.service.ContactOtpService.VerifyOutcome;
import zw.gov.mohcc.impilo.experience.service.ContactOtpService.VerifyResult;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContactOtpServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private NotificationServiceClient notificationClient;

    private ContactOtpService service;

    /** Simple in-memory stand-in for the Redis value store. */
    private final Map<String, String> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new ContactOtpService(redis, notificationClient, new ObjectMapper(), "+263");
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.hasKey(anyString())).thenAnswer(inv -> store.containsKey(inv.getArgument(0, String.class)));
        when(redis.getExpire(anyString())).thenReturn(120L);
        when(redis.delete(anyString())).thenAnswer(inv -> store.remove(inv.getArgument(0, String.class)) != null);
        when(valueOps.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0, String.class)));
        org.mockito.Mockito.doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        when(valueOps.increment(anyString())).thenReturn(1L);
    }

    @Nested
    @DisplayName("Contact normalization")
    class Normalization {

        @Test
        void nationalZimbabweNumberBecomesE164() {
            Contact contact = service.normalize(null, "0771 234 567");
            assertThat(contact.channel()).isEqualTo("PHONE");
            assertThat(contact.normalizedValue()).isEqualTo("+263771234567");
        }

        @Test
        void internationalNumberKept() {
            assertThat(service.normalize("PHONE", "+263771234567").normalizedValue())
                    .isEqualTo("+263771234567");
        }

        @Test
        void emailLowercased() {
            Contact contact = service.normalize(null, " Jane.Doe@Example.ORG ");
            assertThat(contact.channel()).isEqualTo("EMAIL");
            assertThat(contact.normalizedValue()).isEqualTo("jane.doe@example.org");
        }

        @Test
        void channelMismatchRejected() {
            assertThatThrownBy(() -> service.normalize("PHONE", "jane@example.org"))
                    .isInstanceOf(ContactOtpService.InvalidContactException.class);
        }

        @Test
        void garbageRejected() {
            assertThatThrownBy(() -> service.normalize(null, "not-a-contact"))
                    .isInstanceOf(ContactOtpService.InvalidContactException.class);
            assertThatThrownBy(() -> service.normalize(null, "  "))
                    .isInstanceOf(ContactOtpService.InvalidContactException.class);
        }

        @Test
        void maskingNeverExposesFullValue() {
            assertThat(ContactOtpService.maskPhone("+263771234567")).doesNotContain("71234");
            assertThat(ContactOtpService.maskEmail("jane@example.org")).startsWith("j***@");
        }
    }

    @Nested
    @DisplayName("Issue — fail-closed, hashed at rest")
    class Issue {

        @Test
        void storesHashedChallengeAndDelivers() {
            Contact contact = service.requestOtp("PHONE", "0771234567", "10.0.0.1");

            assertThat(contact.maskedValue()).contains("*");
            var challenge = service.readChallenge("+263771234567");
            assertThat(challenge).isNotNull();
            assertThat(challenge.path("codeHash").asText()).hasSize(64); // sha256 hex, not the code
            assertThat(challenge.path("attempts").asInt()).isZero();

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(notificationClient).sendNotification(captor.capture());
            Map<String, Object> sent = captor.getValue();
            assertThat(sent.get("templateKey")).isEqualTo("CONTACT_VERIFY_OTP");
            assertThat(sent.get("channel")).isEqualTo("SMS");
            assertThat(sent.get("to")).isEqualTo("+263771234567");
            @SuppressWarnings("unchecked")
            Map<String, String> vars = (Map<String, String>) sent.get("variables");
            assertThat(vars.get("otp")).matches("\\d{6}");
        }

        @Test
        void emailUsesEmailTemplateAndChannel() {
            service.requestOtp(null, "jane@example.org", null);
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(notificationClient).sendNotification(captor.capture());
            assertThat(captor.getValue().get("templateKey")).isEqualTo("CONTACT_VERIFY_OTP_EMAIL");
            assertThat(captor.getValue().get("channel")).isEqualTo("EMAIL");
        }

        @Test
        void deliveryRejectionDestroysChallenge_failClosed() {
            when(notificationClient.sendNotification(any())).thenThrow(new RuntimeException("notify down"));

            assertThatThrownBy(() -> service.requestOtp("PHONE", "0771234567", null))
                    .isInstanceOf(ContactOtpService.OtpUnavailableException.class);
            assertThat(service.readChallenge("+263771234567")).isNull();
        }

        @Test
        void resendCooldownEnforced() {
            service.requestOtp("PHONE", "0771234567", null);
            assertThatThrownBy(() -> service.requestOtp("PHONE", "0771234567", null))
                    .isInstanceOf(ContactOtpService.OtpRateLimitedException.class);
        }

        @Test
        void perValueWindowEnforced() {
            when(valueOps.increment(startsWith("auth:contact-otp:rl:v:")))
                    .thenReturn((long) ContactOtpService.MAX_REQUESTS_PER_VALUE + 1);
            assertThatThrownBy(() -> service.requestOtp("PHONE", "0771234567", null))
                    .isInstanceOf(ContactOtpService.OtpRateLimitedException.class);
            verify(notificationClient, never()).sendNotification(any());
        }

        @Test
        void redisFailureIsUnavailable_notSilent() {
            when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
            assertThatThrownBy(() -> service.requestOtp("PHONE", "0771234567", null))
                    .isInstanceOf(ContactOtpService.OtpUnavailableException.class);
        }
    }

    @Nested
    @DisplayName("Verify — single use, 5-attempt lockout")
    class Verify {

        private String issueAndCaptureOtp() {
            service.requestOtp("PHONE", "0771234567", null);
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(notificationClient).sendNotification(captor.capture());
            @SuppressWarnings("unchecked")
            Map<String, String> vars = (Map<String, String>) captor.getValue().get("variables");
            return vars.get("otp");
        }

        @Test
        void correctCodeVerifiesAndIsSingleUse() {
            String otp = issueAndCaptureOtp();

            VerifyResult first = service.verifyOtp("0771234567", otp);
            assertThat(first.outcome()).isEqualTo(VerifyOutcome.VERIFIED);
            assertThat(first.contact().normalizedValue()).isEqualTo("+263771234567");

            VerifyResult replay = service.verifyOtp("0771234567", otp);
            assertThat(replay.outcome()).isEqualTo(VerifyOutcome.EXPIRED_OR_UNKNOWN);
        }

        @Test
        void wrongCodeIsInvalidAndCountsAttempt() {
            issueAndCaptureOtp();
            VerifyResult result = service.verifyOtp("0771234567", "000000");
            assertThat(result.outcome()).isEqualTo(VerifyOutcome.INVALID_CODE);
            assertThat(service.readChallenge("+263771234567").path("attempts").asInt()).isEqualTo(1);
        }

        @Test
        void fifthWrongAttemptLocksAndDestroysChallenge() {
            String otp = issueAndCaptureOtp();
            for (int i = 0; i < ContactOtpService.MAX_ATTEMPTS - 1; i++) {
                assertThat(service.verifyOtp("0771234567", "000000").outcome())
                        .isEqualTo(VerifyOutcome.INVALID_CODE);
            }
            assertThat(service.verifyOtp("0771234567", "000000").outcome())
                    .isEqualTo(VerifyOutcome.LOCKED);
            // Even the correct code no longer works — challenge destroyed.
            assertThat(service.verifyOtp("0771234567", otp).outcome())
                    .isEqualTo(VerifyOutcome.EXPIRED_OR_UNKNOWN);
        }

        @Test
        void unknownContactIsExpiredOrUnknown() {
            assertThat(service.verifyOtp("0779999999", "123456").outcome())
                    .isEqualTo(VerifyOutcome.EXPIRED_OR_UNKNOWN);
        }
    }
}
