package zw.gov.mohcc.impilo.mushex;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.RemittanceTokenEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.RemittanceStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.RemittanceTokenRepository;
import zw.gov.mohcc.impilo.mushex.service.HmacService;
import zw.gov.mohcc.impilo.mushex.service.RemittanceService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RemittanceService}.
 *
 * Validates remittance slip issuance (token + OTP hashing), claim flow,
 * expiry enforcement, duplicate-claim prevention, and rate limiting.
 * Uses a real {@link HmacService} with test pepper for hash verification.
 */
@ExtendWith(MockitoExtension.class)
class RemittanceServiceTest {

    @Mock private RemittanceTokenRepository tokenRepository;
    @Mock private PaymentIntentRepository intentRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ObjectMapper objectMapper;

    private HmacService hmacService;
    private RemittanceService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private static final String TEST_PEPPER = "test-pepper";

    @BeforeEach
    void setUp() {
        hmacService = new HmacService(TEST_PEPPER);
        service = new RemittanceService(
            tokenRepository, intentRepository, outboxRepository, hmacService, objectMapper
        );
        TrustContextHolder.set(new TrustContext(
            tenantId, "actor-1", "FACILITY_FINANCE", "BILLING",
            "device-1", UUID.randomUUID(), facilityId, null, null, AccessMode.INTERNAL
        ));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    // ---------------------------------------------------------------
    // issueSlip
    // ---------------------------------------------------------------

    @Test
    void issueSlip_createsTokenWithHashesAndReturnsPlaintext() throws Exception {
        PaymentIntentEntity intent = buildPaidIntent("INT-100");
        when(intentRepository.findById("INT-100")).thenReturn(Optional.of(intent));
        when(tokenRepository.countByIntentIdAndStatusAndCreatedAtAfter(
            anyString(), any(RemittanceStatus.class), any(OffsetDateTime.class))).thenReturn(0L);
        when(tokenRepository.save(any(RemittanceTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        RemittanceService.SlipResult result = service.issueSlip("INT-100");

        assertNotNull(result);
        assertNotNull(result.token());
        assertNotNull(result.otp());
        assertFalse(result.token().isBlank());
        assertFalse(result.otp().isBlank());

        // Verify the token entity was saved with hashes, not plaintext
        ArgumentCaptor<RemittanceTokenEntity> captor = ArgumentCaptor.forClass(RemittanceTokenEntity.class);
        verify(tokenRepository).save(captor.capture());
        RemittanceTokenEntity saved = captor.getValue();

        assertNotNull(saved.getTokenHash());
        assertNotNull(saved.getOtpHash());
        assertNotEquals(result.token(), saved.getTokenHash(), "Token hash must differ from plaintext token");
        assertNotEquals(result.otp(), saved.getOtpHash(), "OTP hash must differ from plaintext OTP");
        assertEquals(RemittanceStatus.ACTIVE, saved.getStatus());
        assertEquals("INT-100", saved.getIntentId());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(OffsetDateTime.now()));

        // Verify outbox event
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void issueSlip_tokenHashIsVerifiable() throws Exception {
        PaymentIntentEntity intent = buildPaidIntent("INT-101");
        when(intentRepository.findById("INT-101")).thenReturn(Optional.of(intent));
        when(tokenRepository.countByIntentIdAndStatusAndCreatedAtAfter(
            anyString(), any(RemittanceStatus.class), any(OffsetDateTime.class))).thenReturn(0L);
        when(tokenRepository.save(any(RemittanceTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        RemittanceService.SlipResult result = service.issueSlip("INT-101");

        // Verify that hashing the plaintext token matches the stored hash
        String recomputedHash = hmacService.hash(result.token());
        ArgumentCaptor<RemittanceTokenEntity> captor = ArgumentCaptor.forClass(RemittanceTokenEntity.class);
        verify(tokenRepository).save(captor.capture());
        assertEquals(recomputedHash, captor.getValue().getTokenHash());
    }

    // ---------------------------------------------------------------
    // claimSlip
    // ---------------------------------------------------------------

    @Test
    void claimSlip_validTokenAndOtp_succeeds() throws Exception {
        String plainToken = "test-token-12345";
        String plainOtp = "123456";
        String tokenHash = hmacService.hash(plainToken);
        String otpHash = hmacService.hash(plainOtp);

        RemittanceTokenEntity token = buildToken("TOK-001", "INT-200", tokenHash, otpHash,
            RemittanceStatus.ACTIVE, OffsetDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(tokenRepository.save(any(RemittanceTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.claimSlip(plainToken, plainOtp);

        assertEquals(RemittanceStatus.CLAIMED, token.getStatus());
        assertNotNull(token.getClaimedAt());
        verify(tokenRepository).save(token);
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void claimSlip_wrongOtp_throws() {
        String plainToken = "test-token-12345";
        String tokenHash = hmacService.hash(plainToken);
        String correctOtpHash = hmacService.hash("123456");

        RemittanceTokenEntity token = buildToken("TOK-002", "INT-201", tokenHash, correctOtpHash,
            RemittanceStatus.ACTIVE, OffsetDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class,
            () -> service.claimSlip(plainToken, "999999"));
    }

    @Test
    void claimSlip_expiredToken_throws() {
        String plainToken = "test-token-expired";
        String plainOtp = "123456";
        String tokenHash = hmacService.hash(plainToken);
        String otpHash = hmacService.hash(plainOtp);

        RemittanceTokenEntity token = buildToken("TOK-003", "INT-202", tokenHash, otpHash,
            RemittanceStatus.ACTIVE, OffsetDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThrows(IllegalStateException.class,
            () -> service.claimSlip(plainToken, plainOtp));
    }

    @Test
    void claimSlip_alreadyClaimed_throws() {
        String plainToken = "test-token-claimed";
        String plainOtp = "123456";
        String tokenHash = hmacService.hash(plainToken);
        String otpHash = hmacService.hash(plainOtp);

        RemittanceTokenEntity token = buildToken("TOK-004", "INT-203", tokenHash, otpHash,
            RemittanceStatus.CLAIMED, OffsetDateTime.now().plusHours(1));
        token.setClaimedAt(OffsetDateTime.now().minusMinutes(5));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThrows(IllegalStateException.class,
            () -> service.claimSlip(plainToken, plainOtp));
    }

    @Test
    void claimSlip_revokedToken_throws() {
        String plainToken = "test-token-revoked";
        String plainOtp = "123456";
        String tokenHash = hmacService.hash(plainToken);
        String otpHash = hmacService.hash(plainOtp);

        RemittanceTokenEntity token = buildToken("TOK-005", "INT-204", tokenHash, otpHash,
            RemittanceStatus.REVOKED, OffsetDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        assertThrows(IllegalStateException.class,
            () -> service.claimSlip(plainToken, plainOtp));
    }

    @Test
    void claimSlip_tokenNotFound_throws() {
        String plainToken = "nonexistent-token";
        when(tokenRepository.findByTokenHash(hmacService.hash(plainToken))).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> service.claimSlip(plainToken, "123456"));
    }

    // ---------------------------------------------------------------
    // Rate limiting
    // ---------------------------------------------------------------

    @Test
    void issueSlip_rateLimitExceeded_throws() {
        PaymentIntentEntity intent = buildPaidIntent("INT-300");
        when(intentRepository.findById("INT-300")).thenReturn(Optional.of(intent));
        when(tokenRepository.countByIntentIdAndStatusAndCreatedAtAfter(
            eq("INT-300"), any(RemittanceStatus.class), any(OffsetDateTime.class))).thenReturn(100L);

        assertThrows(IllegalStateException.class,
            () -> service.issueSlip("INT-300"));

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void issueSlip_belowRateLimit_succeeds() throws Exception {
        PaymentIntentEntity intent = buildPaidIntent("INT-301");
        when(intentRepository.findById("INT-301")).thenReturn(Optional.of(intent));
        when(tokenRepository.countByIntentIdAndStatusAndCreatedAtAfter(
            anyString(), any(RemittanceStatus.class), any(OffsetDateTime.class))).thenReturn(2L);
        when(tokenRepository.save(any(RemittanceTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        RemittanceService.SlipResult result = service.issueSlip("INT-301");

        assertNotNull(result);
        verify(tokenRepository).save(any(RemittanceTokenEntity.class));
    }

    // ---------------------------------------------------------------
    // Claim outbox event
    // ---------------------------------------------------------------

    @Test
    void claimSlip_publishesRemittanceClaimedEvent() throws Exception {
        String plainToken = "test-token-event";
        String plainOtp = "654321";
        String tokenHash = hmacService.hash(plainToken);
        String otpHash = hmacService.hash(plainOtp);

        RemittanceTokenEntity token = buildToken("TOK-010", "INT-400", tokenHash, otpHash,
            RemittanceStatus.ACTIVE, OffsetDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(tokenRepository.save(any(RemittanceTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.claimSlip(plainToken, plainOtp);

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("REMITTANCE_CLAIMED", outboxCaptor.getValue().getEventType());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private PaymentIntentEntity buildPaidIntent(String intentId) {
        PaymentIntentEntity intent = new PaymentIntentEntity();
        intent.setIntentId(intentId);
        intent.setTenantId(tenantId);
        intent.setFacilityId(facilityId);
        intent.setStatus(IntentStatus.PAID);
        intent.setAmountTotal(new BigDecimal("100.00"));
        intent.setAmountPaid(new BigDecimal("100.00"));
        intent.setCurrency("USD");
        intent.setSourceType(SourceType.COSTA_BILL);
        intent.setSourceId("BILL-" + intentId);
        return intent;
    }

    private RemittanceTokenEntity buildToken(String id, String intentId,
                                             String tokenHash, String otpHash,
                                             RemittanceStatus status,
                                             OffsetDateTime expiresAt) {
        RemittanceTokenEntity token = new RemittanceTokenEntity();
        token.setId(id);
        token.setIntentId(intentId);
        token.setTokenHash(tokenHash);
        token.setOtpHash(otpHash);
        token.setStatus(status);
        token.setExpiresAt(expiresAt);
        token.setCreatedAt(OffsetDateTime.now());
        return token;
    }
}
