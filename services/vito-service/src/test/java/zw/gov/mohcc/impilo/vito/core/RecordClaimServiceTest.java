package zw.gov.mohcc.impilo.vito.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.crypto.Argon2IdService;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientAuthorizationLinkEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.RecordClaimChallengeEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientAuthorizationLinkRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.RecordClaimChallengeRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Identity Journey Doctrine §2 / CJ3 — the claim second factor: OTP to the
 * RECORDED contact, bind only on verified possession.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecordClaimService")
class RecordClaimServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock private ClientRepository clientRepository;
    @Mock private RecordClaimChallengeRepository challengeRepository;
    @Mock private ClientAuthorizationLinkRepository linkRepository;

    private RecordClaimService service;
    private final Argon2IdService argon2 = new Argon2IdService(16384, 2, 1); // fast test params

    @BeforeEach
    void setUp() {
        service = new RecordClaimService(clientRepository, challengeRepository, linkRepository,
                argon2, new ObjectMapper());
        when(challengeRepository.save(any(RecordClaimChallengeEntity.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private ClientEntity clientWithPhone(String phone) {
        ClientEntity c = new ClientEntity();
        c.setTenantId(TENANT);
        c.setHealthId(HID);
        c.setContacts("{\"phone\":\"" + phone + "\"}");
        return c;
    }

    @Test
    @DisplayName("start issues a deliverable challenge + masked hint for a recorded phone")
    void start_deliverableWithMask() {
        when(clientRepository.findByTenantIdAndHealthId(TENANT, HID))
                .thenReturn(Optional.of(clientWithPhone("+263771234567")));

        RecordClaimService.ClaimChallenge ch = service.startClaim(TENANT, HID);

        assertTrue(ch.deliverable());
        assertEquals("phone", ch.channel());
        assertEquals("***4567", ch.maskedContact());
        assertEquals("+263771234567", ch.contact(), "raw contact returned once for internal delivery");
        assertEquals(6, ch.otp().length());
        assertFalse(ch.maskedContact().contains("123"), "hint must not reveal the full number");
    }

    @Test
    @DisplayName("no recorded contact → challenge issued but not deliverable (assisted path)")
    void start_noContactNotDeliverable() {
        ClientEntity c = new ClientEntity();
        c.setTenantId(TENANT); c.setHealthId(HID); c.setContacts(null);
        when(clientRepository.findByTenantIdAndHealthId(TENANT, HID)).thenReturn(Optional.of(c));

        RecordClaimService.ClaimChallenge ch = service.startClaim(TENANT, HID);
        assertFalse(ch.deliverable());
        assertNull(ch.contact());
    }

    @Test
    @DisplayName("correct OTP verifies and binds the account (RECORD_LINKED); wrong OTP does not")
    void verify_bindsOnlyOnCorrectOtp() {
        // capture the challenge start persisted, then feed it back for verify
        when(clientRepository.findByTenantIdAndHealthId(TENANT, HID))
                .thenReturn(Optional.of(clientWithPhone("+263771234567")));
        RecordClaimService.ClaimChallenge started = service.startClaim(TENANT, HID);

        RecordClaimChallengeEntity stored = new RecordClaimChallengeEntity();
        stored.setChallengeId(started.challengeId());
        stored.setTenantId(TENANT);
        stored.setClientHealthId(HID);
        stored.setOtpHash(argon2.hash(started.otp()));
        stored.setStatus("PENDING");
        stored.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        stored.setMaxAttempts(5);
        when(challengeRepository.findByChallengeId(started.challengeId())).thenReturn(Optional.of(stored));
        when(linkRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(TENANT, HID)).thenReturn(List.of());

        // wrong code first
        assertFalse(service.verifyClaim(started.challengeId(), "000000", "kc-subject-1").verified());
        verify(linkRepository, never()).save(any());

        // correct code binds
        stored.setStatus("PENDING"); // reset for the positive path (same mock instance)
        RecordClaimService.ClaimVerification v =
                service.verifyClaim(started.challengeId(), started.otp(), "kc-subject-1");
        assertTrue(v.verified());
        assertEquals(HID, v.healthId());

        ArgumentCaptor<ClientAuthorizationLinkEntity> cap = ArgumentCaptor.forClass(ClientAuthorizationLinkEntity.class);
        verify(linkRepository).save(cap.capture());
        assertEquals("ACCOUNT", cap.getValue().getAuthorisationType());
        assertEquals("kc-subject-1", cap.getValue().getReferenceId());
    }

    @Test
    @DisplayName("expired challenge never verifies")
    void verify_expired() {
        RecordClaimChallengeEntity expired = new RecordClaimChallengeEntity();
        UUID cid = UUID.randomUUID();
        expired.setChallengeId(cid);
        expired.setTenantId(TENANT); expired.setClientHealthId(HID);
        expired.setOtpHash(argon2.hash("123456"));
        expired.setStatus("PENDING");
        expired.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        expired.setMaxAttempts(5);
        when(challengeRepository.findByChallengeId(cid)).thenReturn(Optional.of(expired));

        assertFalse(service.verifyClaim(cid, "123456", "acct").verified());
        verify(linkRepository, never()).save(any());
    }
}
