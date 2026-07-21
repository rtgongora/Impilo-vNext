package zw.gov.mohcc.impilo.rito.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.rito.events.RitoEventEmitter;
import zw.gov.mohcc.impilo.rito.persistence.entity.ProviderRatingEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.RatingModerationEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.ProviderRatingRepository;
import zw.gov.mohcc.impilo.rito.persistence.repository.RatingModerationRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rating moderation state machine (RW3): SUBMITTED → UNDER_REVIEW →
 * PUBLISHED | WITHHELD | DISPUTED → RESOLVED. Manipulation flags (coordinated /
 * retaliation / review-bombing) gate publication. Only a rating in a PUBLISHED
 * moderation state is eligible to influence a public reputation summary (RW4).
 *
 * <p>Moderation writes only Rito records — it never touches provider authority.
 */
@Service
public class RatingModerationService {

    private static final Logger log = LoggerFactory.getLogger(RatingModerationService.class);

    private final ProviderRatingRepository ratingRepository;
    private final RatingModerationRepository moderationRepository;
    private final RitoEventEmitter emitter;

    public RatingModerationService(ProviderRatingRepository ratingRepository,
                                   RatingModerationRepository moderationRepository,
                                   RitoEventEmitter emitter) {
        this.ratingRepository = ratingRepository;
        this.moderationRepository = moderationRepository;
        this.emitter = emitter;
    }

    /**
     * Apply a moderation decision to a rating. Decisions map to states:
     * PUBLISH→PUBLISHED, WITHHOLD→WITHHELD, UPHOLD_DISPUTE→WITHHELD,
     * REJECT_DISPUTE→PUBLISHED. Persists a moderation record and updates the rating's
     * current moderation_state.
     */
    @Transactional
    public RatingModerationEntity moderate(UUID tenantId, String reviewerActorId, UUID ratingId,
                                           String decision, String reason, List<String> manipulationFlags) {
        ProviderRatingEntity rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new IllegalArgumentException("Rating not found: " + ratingId));
        if (!tenantId.equals(rating.getTenantId())) {
            throw new SecurityException("Tenant isolation violation: rating belongs to a different tenant");
        }

        String newState = switch (decision == null ? "" : decision.trim().toUpperCase()) {
            case "PUBLISH", "REJECT_DISPUTE" -> "PUBLISHED";
            case "WITHHOLD", "UPHOLD_DISPUTE" -> "WITHHELD";
            case "DISPUTE" -> "DISPUTED";
            case "REVIEW" -> "UNDER_REVIEW";
            default -> throw new IllegalArgumentException("Unknown moderation decision: " + decision);
        };

        RatingModerationEntity moderation = new RatingModerationEntity();
        moderation.setRatingId(rating.getId());
        moderation.setTenantId(tenantId);
        moderation.setState(newState);
        moderation.setReviewerActorId(reviewerActorId);
        moderation.setDecision(decision);
        moderation.setDecisionReason(reason);
        if (manipulationFlags != null && !manipulationFlags.isEmpty()) {
            moderation.setManipulationFlags(toJsonArray(manipulationFlags));
        }
        moderation = moderationRepository.save(moderation);

        rating.setModerationState(newState);
        ratingRepository.save(rating);

        emitter.emit("PROVIDER_RATING", rating.getId().toString(), "rito.rating.moderated",
                "PROVIDER", rating.getProviderPublicId(),
                Map.of("ratingId", rating.getId().toString(), "state", newState,
                        "decision", decision != null ? decision : ""), tenantId);

        log.info("Rating {} moderated → {} by {}", rating.getId(), newState, reviewerActorId);
        return moderation;
    }

    /**
     * Lightweight review-bombing heuristic: an unverified rating for a provider who has
     * an unusually large number of unverified ratings is flagged for a reviewer. Returns
     * the flags to attach (never auto-withholds — a human decides).
     */
    @Transactional(readOnly = true)
    public List<String> detectManipulation(UUID tenantId, ProviderRatingEntity rating) {
        if (Boolean.TRUE.equals(rating.getVerifiedInteraction())) {
            return List.of();
        }
        long unverified = ratingRepository
                .findByTenantIdAndProviderPublicId(tenantId, rating.getProviderPublicId()).stream()
                .filter(r -> !Boolean.TRUE.equals(r.getVerifiedInteraction()))
                .count();
        return unverified >= 10 ? List.of("REVIEW_BOMBING_SUSPECTED") : List.of();
    }

    private static String toJsonArray(List<String> flags) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < flags.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(flags.get(i).replace("\"", "")).append('"');
        }
        return sb.append(']').toString();
    }
}
