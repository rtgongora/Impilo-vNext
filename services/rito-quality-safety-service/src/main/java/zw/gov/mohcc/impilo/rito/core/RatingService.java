package zw.gov.mohcc.impilo.rito.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.DomainScoreInput;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.ProviderResponseRequest;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.SubmitRatingRequest;
import zw.gov.mohcc.impilo.rito.events.RitoEventEmitter;
import zw.gov.mohcc.impilo.rito.persistence.entity.*;
import zw.gov.mohcc.impilo.rito.persistence.repository.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Provider rating submission + provider response (RW3). Ratings are contextual and, when
 * they reference a recorded PCT verified interaction, stamped verified — with a strict
 * one-verified-rating-per-(encounter, provider) invariant. Domains are stored separately.
 *
 * <p><b>Regulation firewall:</b> this service writes ONLY Rito rating/moderation records.
 * It performs no Varapi / TSHEPO / Vashandi authority mutation — a rating never changes a
 * provider's licence, scope, employment or access. That invariant is asserted by test (RW7).
 */
@Service
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ProviderRatingRepository ratingRepository;
    private final RatingDomainScoreRepository domainScoreRepository;
    private final RatingModerationRepository moderationRepository;
    private final ProviderResponseRepository providerResponseRepository;
    private final VerifiedInteractionRepository verifiedInteractionRepository;
    private final RitoEventEmitter emitter;

    public RatingService(ProviderRatingRepository ratingRepository,
                         RatingDomainScoreRepository domainScoreRepository,
                         RatingModerationRepository moderationRepository,
                         ProviderResponseRepository providerResponseRepository,
                         VerifiedInteractionRepository verifiedInteractionRepository,
                         RitoEventEmitter emitter) {
        this.ratingRepository = ratingRepository;
        this.domainScoreRepository = domainScoreRepository;
        this.moderationRepository = moderationRepository;
        this.providerResponseRepository = providerResponseRepository;
        this.verifiedInteractionRepository = verifiedInteractionRepository;
        this.emitter = emitter;
    }

    @Transactional
    public ProviderRatingEntity submitRating(UUID tenantId, SubmitRatingRequest req) {
        if (req.providerPublicId() == null || req.providerPublicId().isBlank()) {
            throw new IllegalArgumentException("providerPublicId is required");
        }

        // Verification gate: a rating tied to a recorded PCT interaction is verified.
        boolean verified = false;
        String verificationSource = "NONE";
        VerifiedInteractionEntity vi = null;
        if (req.encounterRef() != null && !req.encounterRef().isBlank()) {
            vi = verifiedInteractionRepository
                    .findByTenantIdAndEncounterRef(tenantId, req.encounterRef()).orElse(null);
            if (vi != null) {
                verified = true;
                verificationSource = "PCT";
                // Anti-manipulation invariant: one verified rating per (encounter, provider).
                if (ratingRepository.existsByTenantIdAndEncounterRefAndProviderPublicIdAndVerifiedInteractionTrue(
                        tenantId, req.encounterRef(), req.providerPublicId())) {
                    throw new IllegalStateException(
                            "A verified rating already exists for this encounter and provider");
                }
            }
        }

        ProviderRatingEntity rating = new ProviderRatingEntity();
        rating.setTenantId(tenantId);
        rating.setProviderPublicId(req.providerPublicId());
        // Prefer the authoritative context from the verified interaction where present.
        rating.setFacilityId(req.facilityId() != null ? req.facilityId()
                : (vi != null ? vi.getFacilityId() : null));
        rating.setServicePointId(req.servicePointId());
        rating.setEncounterRef(req.encounterRef());
        rating.setProviderRole(req.providerRole());
        rating.setModality(req.modality() != null ? req.modality() : (vi != null ? vi.getModality() : null));
        rating.setSpecialty(req.specialty());
        rating.setVerifiedInteraction(verified);
        rating.setVerificationSource(verificationSource);
        rating.setRespondentClass(req.respondentClass() != null ? req.respondentClass() : "CLIENT");
        rating.setRespondentIdentityProtected(
                req.respondentIdentityProtected() == null || req.respondentIdentityProtected());
        // Identity ref is only retained when the respondent is NOT identity-protected.
        rating.setRespondentActorRef(Boolean.FALSE.equals(rating.getRespondentIdentityProtected())
                ? req.respondentActorRef() : null);
        rating.setReportingPeriod(req.reportingPeriod() != null ? req.reportingPeriod()
                : OffsetDateTime.now().format(PERIOD));
        rating.setOverallScore(req.overallScore());
        rating.setNarrativeCaseId(req.narrativeCaseId());
        rating.setModerationState("SUBMITTED");
        rating = ratingRepository.save(rating);

        if (req.domainScores() != null) {
            for (DomainScoreInput ds : req.domainScores()) {
                if (ds == null || ds.domain() == null || ds.measure() == null || ds.score() == null) {
                    continue;
                }
                RatingDomainScoreEntity score = new RatingDomainScoreEntity();
                score.setRatingId(rating.getId());
                score.setTenantId(tenantId);
                score.setDomain(ds.domain());
                score.setMeasure(ds.measure());
                score.setScore(ds.score());
                if (ds.scale() != null) {
                    score.setScale(ds.scale());
                }
                domainScoreRepository.save(score);
            }
        }

        // Open the moderation record. Unverified public feedback starts UNDER_REVIEW;
        // verified feedback is eligible for straight-through publication (RW3 moderation).
        RatingModerationEntity moderation = new RatingModerationEntity();
        moderation.setRatingId(rating.getId());
        moderation.setTenantId(tenantId);
        moderation.setState(verified ? "SUBMITTED" : "UNDER_REVIEW");
        moderationRepository.save(moderation);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ratingId", rating.getId().toString());
        payload.put("providerPublicId", rating.getProviderPublicId());
        payload.put("facilityId", rating.getFacilityId() != null ? rating.getFacilityId().toString() : null);
        payload.put("verified", verified);
        payload.put("reportingPeriod", rating.getReportingPeriod());
        emitter.emit("PROVIDER_RATING", rating.getId().toString(), "rito.rating.submitted",
                "PROVIDER", rating.getProviderPublicId(), payload, tenantId);

        log.info("Rating submitted id={} provider={} verified={}",
                rating.getId(), rating.getProviderPublicId(), verified);
        return rating;
    }

    @Transactional
    public ProviderResponseEntity respond(UUID tenantId, UUID ratingId, ProviderResponseRequest req) {
        ProviderRatingEntity rating = requireRating(tenantId, ratingId);
        ProviderResponseEntity response = new ProviderResponseEntity();
        response.setRatingId(rating.getId());
        response.setTenantId(tenantId);
        response.setProviderPublicId(req.providerPublicId() != null ? req.providerPublicId()
                : rating.getProviderPublicId());
        response.setResponseText(req.responseText());
        response.setRespondedBy(req.respondedBy());
        response.setStatus("SUBMITTED");
        response = providerResponseRepository.save(response);

        emitter.emit("PROVIDER_RATING", rating.getId().toString(), "rito.rating.provider_responded",
                "PROVIDER", rating.getProviderPublicId(), Map.of("ratingId", rating.getId().toString()), tenantId);
        return response;
    }

    private ProviderRatingEntity requireRating(UUID tenantId, UUID ratingId) {
        ProviderRatingEntity rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new IllegalArgumentException("Rating not found: " + ratingId));
        if (!tenantId.equals(rating.getTenantId())) {
            throw new SecurityException("Tenant isolation violation: rating belongs to a different tenant");
        }
        return rating;
    }
}
