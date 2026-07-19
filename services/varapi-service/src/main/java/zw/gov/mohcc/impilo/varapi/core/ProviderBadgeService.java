package zw.gov.mohcc.impilo.varapi.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBadgeEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderBadgeRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Provider badges (PJ6, D-P5): issue a serial + signed QR payload, revoke the
 * SERIAL ONLY on loss, and serve the public scan lane. The badge never
 * authorises: scanning yields live public register facts (or one uniform
 * NOT_RECOGNISED — fake, revoked and not-in-standing are indistinguishable to
 * the public), and tap-to-login merely prefills the account selector.
 */
@Service
public class ProviderBadgeService {

    private static final Logger log = LoggerFactory.getLogger(ProviderBadgeService.class);

    private final ProviderRepository providerRepository;
    private final ProviderBadgeRepository badgeRepository;
    private final BadgeSigningService signingService;
    private final PublicPractitionerProjectionSupport projectionSupport;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public ProviderBadgeService(ProviderRepository providerRepository,
                                ProviderBadgeRepository badgeRepository,
                                BadgeSigningService signingService,
                                PublicPractitionerProjectionSupport projectionSupport,
                                ObjectMapper objectMapper) {
        this.providerRepository = providerRepository;
        this.badgeRepository = badgeRepository;
        this.signingService = signingService;
        this.projectionSupport = projectionSupport;
        this.objectMapper = objectMapper;
    }

    public record IssuedBadge(String badgeSerial, String qrPayload) {}

    /** Public-scan result: one uniform shape; register facts only when verified. */
    public record BadgeVerification(String status, String displayName, String profession,
                                    String cadre, String registerStatus,
                                    java.time.LocalDate licenceValidFrom,
                                    java.time.LocalDate licenceValidTo) {
        static final BadgeVerification NOT_RECOGNISED =
                new BadgeVerification("NOT_RECOGNISED", null, null, null, null, null, null);
    }

    @Transactional
    public IssuedBadge issue(String providerPublicId, String printJobRef) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderEntity provider = providerRepository
                .findByProviderPublicIdAndTenantId(
                        providerPublicId.trim().toUpperCase(Locale.ROOT), ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider"));

        byte[] raw = new byte[10];
        secureRandom.nextBytes(raw);
        String serial = "BDG-" + HexFormat.of().formatHex(raw).toUpperCase(Locale.ROOT);

        ProviderBadgeEntity badge = new ProviderBadgeEntity();
        badge.setTenantId(ctx.tenantId());
        badge.setBadgeSerial(serial);
        badge.setProviderId(provider.getId());
        badge.setPrintJobRef(printJobRef);
        badge.setIssuedBy(ctx.actorId());
        badgeRepository.save(badge);

        // QR carries serial + provider public id ONLY — never HID/National ID/
        // privileges (doctrine §6 card matrix). Signed so a printed QR cannot be
        // forged; the LIVE status still comes from the registry at scan time.
        String payload;
        try {
            payload = objectMapper.writeValueAsString(java.util.Map.of(
                    "serial", serial, "provider", provider.getProviderPublicId()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build badge payload", e);
        }
        String qr = signingService.sign(payload);
        log.info("provider badge {} issued for {}", serial, provider.getProviderPublicId());
        return new IssuedBadge(serial, qr);
    }

    /** Lost badge = revoke the serial only; the Provider ID is untouched. */
    @Transactional
    public ProviderBadgeEntity revoke(String badgeSerial, String status, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderBadgeEntity badge = badgeRepository.findByBadgeSerial(badgeSerial.trim())
                .filter(b -> ctx.tenantId().equals(b.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown badge serial"));
        if (!ProviderBadgeEntity.STATUS_ACTIVE.equals(badge.getStatus())) {
            return badge; // idempotent
        }
        badge.setStatus(ProviderBadgeEntity.STATUS_LOST.equalsIgnoreCase(status)
                ? ProviderBadgeEntity.STATUS_LOST : ProviderBadgeEntity.STATUS_REVOKED);
        badge.setRevokedAt(java.time.Instant.now());
        badge.setRevokeReason(reason);
        badge = badgeRepository.save(badge);
        log.warn("provider badge {} {} ({})", badgeSerial, badge.getStatus(), reason);
        return badge;
    }

    /**
     * Public scan lane: verify the signed QR payload and return live register
     * facts. EVERY failure — bad signature, unknown serial, revoked badge,
     * provider not in public standing — collapses to one NOT_RECOGNISED shape.
     */
    @Transactional(readOnly = true)
    public BadgeVerification verifyScan(String compactPayload) {
        String payload = signingService.verify(compactPayload);
        if (payload == null) {
            return BadgeVerification.NOT_RECOGNISED;
        }
        String serial;
        try {
            JsonNode node = objectMapper.readTree(payload);
            serial = node.path("serial").asText(null);
        } catch (Exception e) {
            return BadgeVerification.NOT_RECOGNISED;
        }
        if (serial == null || serial.isBlank()) {
            return BadgeVerification.NOT_RECOGNISED;
        }

        Optional<ProviderBadgeEntity> badge = badgeRepository.findByBadgeSerial(serial)
                .filter(b -> ProviderBadgeEntity.STATUS_ACTIVE.equals(b.getStatus()));
        if (badge.isEmpty()) {
            return BadgeVerification.NOT_RECOGNISED;
        }
        Optional<ProviderEntity> provider = providerRepository.findById(badge.get().getProviderId())
                .filter(ProviderEntity::isActive);
        if (provider.isEmpty()) {
            return BadgeVerification.NOT_RECOGNISED;
        }

        ProviderEntity p = provider.get();
        PublicPractitionerProjectionSupport.LicenceWindow window =
                projectionSupport.resolveLicenceWindow(p.getTenantId(), p.getId());
        return new BadgeVerification(
                "VERIFIED",
                PublicPractitionerProjectionSupport.displayName(p),
                p.getProfession(),
                p.getCadre(),
                PublicPractitionerProjectionSupport.normalizeStatus(p.getRegistryStatus()),
                window.validFrom(),
                window.validTo());
    }
}
