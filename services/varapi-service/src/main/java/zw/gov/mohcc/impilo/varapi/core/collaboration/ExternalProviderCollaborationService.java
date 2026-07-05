package zw.gov.mohcc.impilo.varapi.core.collaboration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.api.dto.collaboration.CouncilSummaryResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.collaboration.CreateExternalProviderProfileRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.collaboration.ExternalProviderProfileResponse;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ExternalProviderAccessProfileEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ExternalProviderVerificationAttemptEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ExternalProviderAccessProfileRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ExternalProviderVerificationAttemptRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Provisional / external provider profiles for patient-mediated collaboration (bounded temp Provider ID).
 */
@Service
public class ExternalProviderCollaborationService {

    // External 5-level ladder, aliased onto the shared platform trust enum
    // (zw.gov.mohcc.impilo.varapi.enums.ProviderTrustLevel). String values are
    // IDENTICAL to the historical literals, so the persisted
    // external_provider_access_profile.trust_level column and all wire payloads
    // are untouched; ProviderTrustLevel.fromExternalLabel maps these onto the
    // platform ladder.
    public static final String TRUST_SELF_ASSERTED =
            zw.gov.mohcc.impilo.varapi.enums.ProviderTrustLevel.EXTERNAL_SELF_ASSERTED;
    public static final String TRUST_COUNCIL_FORMAT =
            zw.gov.mohcc.impilo.varapi.enums.ProviderTrustLevel.EXTERNAL_COUNCIL_NUMBER_FORMAT_VALID;
    public static final String TRUST_COUNCIL_FOUND =
            zw.gov.mohcc.impilo.varapi.enums.ProviderTrustLevel.EXTERNAL_COUNCIL_NUMBER_FOUND;
    public static final String TRUST_COUNCIL_IDENTITY =
            zw.gov.mohcc.impilo.varapi.enums.ProviderTrustLevel.EXTERNAL_COUNCIL_AND_IDENTITY_MATCH;
    public static final String TRUST_FULLY_LINKED =
            zw.gov.mohcc.impilo.varapi.enums.ProviderTrustLevel.EXTERNAL_FULLY_LINKED_OR_ONBOARDED;

    private final ExternalProviderAccessProfileRepository profileRepository;
    private final ExternalProviderVerificationAttemptRepository verificationRepository;
    private final CouncilRepository councilRepository;
    private final ProviderCouncilRegistrationRecordRepository councilRegistrationRecordRepository;
    private final EventOutboxRepository outboxRepository;

    public ExternalProviderCollaborationService(
            ExternalProviderAccessProfileRepository profileRepository,
            ExternalProviderVerificationAttemptRepository verificationRepository,
            CouncilRepository councilRepository,
            ProviderCouncilRegistrationRecordRepository councilRegistrationRecordRepository,
            EventOutboxRepository outboxRepository) {
        this.profileRepository = profileRepository;
        this.verificationRepository = verificationRepository;
        this.councilRepository = councilRepository;
        this.councilRegistrationRecordRepository = councilRegistrationRecordRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public ExternalProviderProfileResponse createProfile(CreateExternalProviderProfileRequest req) {
        CouncilEntity council = councilRepository
                .findByIdAndTenantId(req.councilId(), req.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown council for tenant"));

        ExternalProviderAccessProfileEntity row = new ExternalProviderAccessProfileEntity();
        row.setTenantId(req.tenantId());
        row.setTemporaryProviderPublicId(generateTemporaryProviderPublicId());
        row.setVitoAccessGrantRef(req.vitoAccessGrantRef());
        row.setCouncil(council);
        row.setCouncilRegistrationNumber(req.councilRegistrationNumber().trim());
        row.setGivenName(trimToNull(req.givenName()));
        row.setFamilyName(trimToNull(req.familyName()));
        row.setProfessionOrCadre(trimToNull(req.professionOrCadre()));
        row.setEmail(trimToNull(req.email()));
        row.setPhone(trimToNull(req.phone()));
        row.setOrganisationHint(trimToNull(req.organisationHint()));
        row.setTrustLevel(TRUST_SELF_ASSERTED);
        row.setVerificationState("PENDING");
        row.setSelfAssertionState("CAPTURED");
        row = profileRepository.save(row);

        VerificationOutcome vo = runCouncilVerification(row, council);
        row.setTrustLevel(vo.trustLevel());
        row.setVerificationState(vo.verificationStateLabel());
        if (vo.matchedProviderPublicId() != null) {
            row.setMatchedProviderPublicId(vo.matchedProviderPublicId());
        }
        profileRepository.save(row);

        publish("EXTERNAL_PROVIDER", row.getTemporaryProviderPublicId(), "external_provider.profile_created",
                "{\"profileId\":" + row.getId() + ",\"grantRef\":\"" + jsonEscape(row.getVitoAccessGrantRef()) + "\"}");

        return toResponse(row, vo.councilRegistrationMatched(), vo.matchedProviderPublicId());
    }

    @Transactional(readOnly = true)
    public ExternalProviderProfileResponse getByTemporaryPublicId(String temporaryProviderPublicId) {
        ExternalProviderAccessProfileEntity row = profileRepository
                .findByTemporaryProviderPublicId(temporaryProviderPublicId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown temporary provider id"));
        boolean matched = row.getMatchedProviderPublicId() != null && !row.getMatchedProviderPublicId().isBlank();
        return toResponse(row, matched, row.getMatchedProviderPublicId());
    }

    @Transactional(readOnly = true)
    public List<CouncilSummaryResponse> listCouncilsForCollaboration(UUID tenantId) {
        return councilRepository.findByTenantId(tenantId).stream()
                .filter(CouncilEntity::isActive)
                .sorted(Comparator.comparing(CouncilEntity::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(c -> new CouncilSummaryResponse(
                        c.getId(),
                        c.getCouncilCode(),
                        c.getName(),
                        c.getCouncilType(),
                        c.getRegistrationNumberPattern()))
                .toList();
    }

    @Transactional
    public ExternalProviderProfileResponse linkToOnboardedProvider(long profileId, String providerPublicId) {
        ExternalProviderAccessProfileEntity row = profileRepository
                .findById(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown profile"));
        row.setLinkedProviderPublicId(providerPublicId.trim());
        row.setTrustLevel(TRUST_FULLY_LINKED);
        row.setVerificationState("LINKED");
        profileRepository.save(row);
        publish("EXTERNAL_PROVIDER", row.getTemporaryProviderPublicId(),
                "external_provider.onboarding_transition_started",
                "{\"profileId\":" + profileId + ",\"providerPublicId\":\"" + jsonEscape(providerPublicId) + "\"}");
        return toResponse(
                row,
                row.getMatchedProviderPublicId() != null && !row.getMatchedProviderPublicId().isBlank(),
                row.getMatchedProviderPublicId());
    }

    private ExternalProviderProfileResponse toResponse(
            ExternalProviderAccessProfileEntity row, boolean councilMatched, String matchedPub) {
        return new ExternalProviderProfileResponse(
                row.getId(),
                row.getTemporaryProviderPublicId(),
                row.getTrustLevel(),
                row.getVerificationState(),
                councilMatched,
                matchedPub,
                row.getLinkedProviderPublicId());
    }

    private VerificationOutcome runCouncilVerification(ExternalProviderAccessProfileEntity row, CouncilEntity council) {
        String reg = row.getCouncilRegistrationNumber();
        if (!councilRegistrationFormatValid(reg, council)) {
            ExternalProviderVerificationAttemptEntity att = newAttempt(
                    row, "COUNCIL_REGISTRATION", "IMPORTED_REGISTRY", "FORMAT_INVALID", null, "Council registration format invalid");
            verificationRepository.save(att);
            return new VerificationOutcome(TRUST_SELF_ASSERTED, "FAILED", false, null);
        }

        Optional<ProviderCouncilRegistrationRecordEntity> hit =
                councilRegistrationRecordRepository.findFirstByTenantIdAndCouncil_IdAndRegistrationNumberIgnoreCase(
                        row.getTenantId(), row.getCouncil().getId(), reg);

        if (hit.isEmpty()) {
            ExternalProviderVerificationAttemptEntity att = newAttempt(
                    row, "COUNCIL_REGISTRATION", "IMPORTED_REGISTRY", "NOT_FOUND", BigDecimal.valueOf(0.25), null);
            verificationRepository.save(att);
            return new VerificationOutcome(TRUST_COUNCIL_FORMAT, "NO_MATCH", false, null);
        }

        ProviderEntity provider = hit.get().getProvider();
        String matchedPub = provider.getProviderPublicId();
        boolean identityMatch = namesRoughlyMatch(row.getGivenName(), row.getFamilyName(), provider);

        String trust = identityMatch ? TRUST_COUNCIL_IDENTITY : TRUST_COUNCIL_FOUND;
        String outcome = identityMatch ? "IDENTITY_ALIGNED" : "REGISTRY_MATCH";
        BigDecimal conf = identityMatch ? BigDecimal.valueOf(0.92) : BigDecimal.valueOf(0.72);

        ExternalProviderVerificationAttemptEntity att = newAttempt(
                row, "COUNCIL_REGISTRATION", "IMPORTED_REGISTRY", outcome, conf, matchedPub);
        verificationRepository.save(att);

        publish("EXTERNAL_PROVIDER", row.getTemporaryProviderPublicId(), "external_provider.verification_attempted",
                "{\"outcome\":\"" + outcome + "\",\"matchedProviderPublicId\":\"" + jsonEscape(matchedPub) + "\"}");

        return new VerificationOutcome(trust, "VERIFIED", true, matchedPub);
    }

    private ExternalProviderVerificationAttemptEntity newAttempt(
            ExternalProviderAccessProfileEntity row,
            String type,
            String source,
            String outcome,
            BigDecimal confidence,
            String matchedPub) {
        ExternalProviderVerificationAttemptEntity e = new ExternalProviderVerificationAttemptEntity();
        e.setProfile(row);
        e.setVerificationType(type);
        e.setSourceType(source);
        e.setOutcome(outcome);
        e.setConfidenceScore(confidence);
        e.setMatchedProviderPublicId(matchedPub);
        return e;
    }

    private static boolean councilRegistrationFormatValid(String reg, CouncilEntity council) {
        if (reg == null) {
            return false;
        }
        String r = reg.trim();
        if (council != null && council.getRegistrationNumberPattern() != null
                && !council.getRegistrationNumberPattern().isBlank()) {
            try {
                return Pattern.compile(council.getRegistrationNumberPattern()).matcher(r).matches();
            } catch (Exception e) {
                return false;
            }
        }
        return r.length() >= 4 && r.length() <= 120 && r.matches("[A-Za-z0-9][A-Za-z0-9-]{2,119}");
    }

    private static boolean namesRoughlyMatch(String given, String family, ProviderEntity provider) {
        if (provider == null) {
            return false;
        }
        String g = norm(given);
        String f = norm(family);
        String pg = norm(provider.getGivenName());
        String pf = norm(provider.getFamilyName());
        if (g.isEmpty() || f.isEmpty() || pg.isEmpty() || pf.isEmpty()) {
            return false;
        }
        return g.equals(pg) && f.equals(pf);
    }

    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String generateTemporaryProviderPublicId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase(Locale.ROOT);
    }

    private void publish(String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record VerificationOutcome(
            String trustLevel,
            String verificationStateLabel,
            boolean councilRegistrationMatched,
            String matchedProviderPublicId) {}
}
