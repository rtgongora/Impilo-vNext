package zw.gov.mohcc.impilo.vito.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.crypto.Argon2IdService;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientAuthorizationLinkEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.RecordClaimChallengeEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientAuthorizationLinkRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.RecordClaimChallengeRepository;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Citizen "claim my record" second factor (Identity Journey Doctrine §2, CJ3).
 *
 * <p>After the Trust Core privately resolves a claim to a Health ID, the claimant
 * must prove possession of a contact <b>already recorded</b> on that record. This
 * service dispatches (well, prepares) a one-time code to that recorded contact and
 * verifies it; only on success is the account bound to the Health ID via a
 * {@code client_authorization_link}. Knowing someone's details is never enough —
 * they must receive the code at the record's own contact.</p>
 *
 * <p>The plaintext OTP + contact are returned to the trusted internal caller (the
 * BFF) exactly once so the BFF can deliver via notification; the client only ever
 * sees a masked hint. Anti-enum: a claim against an unmatched record still yields a
 * challenge shape, but with no deliverable contact it can never verify.</p>
 */
@Service
public class RecordClaimService {

    private static final Logger log = LoggerFactory.getLogger(RecordClaimService.class);
    private static final int OTP_LENGTH = 6;
    private static final int EXPIRY_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClientRepository clientRepository;
    private final RecordClaimChallengeRepository challengeRepository;
    private final ClientAuthorizationLinkRepository linkRepository;
    private final Argon2IdService argon2IdService;
    private final ObjectMapper objectMapper;

    public RecordClaimService(ClientRepository clientRepository,
                              RecordClaimChallengeRepository challengeRepository,
                              ClientAuthorizationLinkRepository linkRepository,
                              Argon2IdService argon2IdService,
                              ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.challengeRepository = challengeRepository;
        this.linkRepository = linkRepository;
        this.argon2IdService = argon2IdService;
        this.objectMapper = objectMapper;
    }

    /** OTP + raw contact are returned once for internal delivery; only maskedContact is client-safe. */
    public record ClaimChallenge(UUID challengeId, String otp, String contact, String maskedContact,
                                 String channel, boolean deliverable) {}

    public record ClaimVerification(boolean verified, UUID healthId) {}

    /**
     * Start a claim: generate a challenge for the resolved Health ID, targeting a
     * contact recorded on the record. If no contact is recorded, the challenge is
     * still created (uniform shape) but not deliverable — the claimant must use an
     * assisted path.
     */
    @Transactional
    public ClaimChallenge startClaim(UUID tenantId, UUID healthId) {
        Optional<ClientEntity> client = clientRepository.findByTenantIdAndHealthId(tenantId, healthId);
        String phone = client.map(c -> contactValue(c, "phone")).orElse(null);
        String email = client.map(c -> contactValue(c, "email")).orElse(null);
        String channel = phone != null ? "phone" : (email != null ? "email" : "none");
        String contact = phone != null ? phone : email;

        String otp = generateOtp();
        RecordClaimChallengeEntity ch = new RecordClaimChallengeEntity();
        ch.setChallengeId(UUID.randomUUID());
        ch.setTenantId(tenantId);
        ch.setClientHealthId(healthId);
        ch.setOtpHash(argon2IdService.hash(otp));
        ch.setChannel(channel);
        ch.setContactMasked(mask(contact, channel));
        ch.setExpiresAt(OffsetDateTime.now().plusMinutes(EXPIRY_MINUTES));
        challengeRepository.save(ch);

        boolean deliverable = contact != null;
        log.info("record-claim challenge {} for health_id={} channel={} deliverable={}",
                ch.getChallengeId(), healthId, channel, deliverable);
        return new ClaimChallenge(ch.getChallengeId(), otp, contact, ch.getContactMasked(), channel, deliverable);
    }

    /**
     * Verify a claim OTP and, on success, bind the account to the Health ID with a
     * RECORD_LINKED authorization link. Enforces expiry + max attempts.
     */
    @Transactional
    public ClaimVerification verifyClaim(UUID challengeId, String code, String accountRef) {
        RecordClaimChallengeEntity ch = challengeRepository.findByChallengeId(challengeId).orElse(null);
        if (ch == null || !"PENDING".equals(ch.getStatus())) {
            return new ClaimVerification(false, null);
        }
        if (ch.getExpiresAt().isBefore(OffsetDateTime.now())) {
            ch.setStatus("EXPIRED");
            challengeRepository.save(ch);
            return new ClaimVerification(false, null);
        }
        if (ch.getAttempts() >= ch.getMaxAttempts()) {
            ch.setStatus("LOCKED");
            challengeRepository.save(ch);
            return new ClaimVerification(false, null);
        }
        ch.setAttempts(ch.getAttempts() + 1);
        boolean ok = code != null && argon2IdService.verify(code, ch.getOtpHash());
        if (!ok) {
            if (ch.getAttempts() >= ch.getMaxAttempts()) {
                ch.setStatus("LOCKED");
            }
            challengeRepository.save(ch);
            return new ClaimVerification(false, null);
        }
        ch.setStatus("VERIFIED");
        ch.setVerifiedAt(OffsetDateTime.now());
        challengeRepository.save(ch);
        bindAccount(ch.getTenantId(), ch.getClientHealthId(), accountRef);
        return new ClaimVerification(true, ch.getClientHealthId());
    }

    /** Idempotently bind an account to the Health ID (RECORD_LINKED evidence). */
    private void bindAccount(UUID tenantId, UUID healthId, String accountRef) {
        if (accountRef == null || accountRef.isBlank()) {
            return;
        }
        boolean already = linkRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(tenantId, healthId)
                .stream()
                .anyMatch(l -> "ACCOUNT".equals(l.getAuthorisationType())
                        && accountRef.equals(l.getReferenceId())
                        && "ACTIVE".equals(l.getStatus()));
        if (already) {
            return;
        }
        ClientAuthorizationLinkEntity link = new ClientAuthorizationLinkEntity();
        link.setAuthorizationLinkId(UUID.randomUUID());
        link.setTenantId(tenantId);
        link.setClientHealthId(healthId);
        link.setAuthorisationType("ACCOUNT");
        link.setReferenceId(accountRef);
        link.setStatus("ACTIVE");
        link.setStartDate(OffsetDateTime.now());
        linkRepository.save(link);
        log.info("account {} bound to health_id={} via verified record claim (RECORD_LINKED)", accountRef, healthId);
    }

    private String contactValue(ClientEntity client, String key) {
        try {
            if (client.getContacts() == null || client.getContacts().isBlank()) {
                return null;
            }
            JsonNode node = objectMapper.readTree(client.getContacts()).path(key);
            return node.isMissingNode() || node.isNull() || node.asText().isBlank() ? null : node.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private static String generateOtp() {
        StringBuilder sb = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private static String mask(String contact, String channel) {
        if (contact == null) {
            return null;
        }
        if ("email".equals(channel)) {
            int at = contact.indexOf('@');
            if (at <= 1) {
                return "***" + (at >= 0 ? contact.substring(at) : "");
            }
            return contact.charAt(0) + "***" + contact.substring(at);
        }
        String digits = contact.replaceAll("[^0-9]", "");
        String last = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        return "***" + last;
    }
}
