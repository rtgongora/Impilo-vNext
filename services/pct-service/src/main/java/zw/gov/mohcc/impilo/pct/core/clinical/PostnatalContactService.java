package zw.gov.mohcc.impilo.pct.core.clinical;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialityCategory;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.PostnatalContactEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.PostnatalContactRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Records a postnatal maternal contact on the mother's continuum.
 *
 * <p>The load-bearing invariants — a danger-sign finding requires a completed screen, a referral
 * carries its reason — are CHECK constraints in V436, so they hold even against a write that bypasses
 * this service. What the service adds is offline idempotency, the boolean-gate defaults (so a null
 * never reads as an affirmative), and the FULL_CLINICAL default. It deliberately does NOT default
 * {@code screeningComplete} to true or {@code dangerSignsPresent} to false: a screen that did not
 * happen must stay unrecorded, not be silently filled with reassurance.
 */
@Service
public class PostnatalContactService {

    private final PostnatalContactRepository contacts;

    private final ConfidentialCarePolicyProvider confidentiality;
    private final ConfidentialRecordGuard confidentialRecords;

    public PostnatalContactService(PostnatalContactRepository contacts,
                                   ConfidentialCarePolicyProvider confidentiality,
                                   ConfidentialRecordGuard confidentialRecords) {
        this.contacts = contacts;
        this.confidentiality = confidentiality;
        this.confidentialRecords = confidentialRecords;
    }

    /**
     * A postnatal contact is SRH content only when it actually carries contraception content. A
     * routine PNC visit is ordinary maternity care and is deliberately left unstamped — over-marking
     * would put half the postnatal register behind a confidentiality grant nobody holds.
     *
     * <p>Deliberately NOT stamped as MENTAL_HEALTH off {@code mood_screen}: that column has no
     * vocabulary and no CHECK, so a trigger from it would be guesswork. Raised as a schema gap
     * instead of invented here.
     */
    private ConfidentialityStamper.Stamp stampFor(PostnatalContactEntity contact) {
        boolean carriesContraception = Boolean.TRUE.equals(contact.getContraceptionDiscussed())
                || contact.getPostpartumContraceptionEpisodeId() != null;
        if (!carriesContraception) {
            return null;
        }
        var actDate = contact.getContactedAt() == null
                ? java.time.LocalDate.now() : contact.getContactedAt().toLocalDate();
        var category = ConfidentialityCategory.SEXUAL_REPRODUCTIVE_HEALTH;
        var policy = confidentiality.policyAsOf(contact.getTenantId(), actDate);
        var subject = confidentiality.subjectAt(contact.getTenantId(), contact.getMotherCpid(), category, actDate);
        return ConfidentialityStamper.gated(
                ConfidentialityStamper.ageGated(category, subject, actDate, policy),
                confidentiality.classStampingEnabled());
    }

    @Transactional(readOnly = true)
    public List<PostnatalContactEntity> forMother(UUID tenantId, String motherCpid) {
        if (tenantId == null || motherCpid == null || motherCpid.isBlank()) {
            return List.of();
        }
        return confidentialRecords.filter(contacts.findByMother(tenantId, motherCpid),
                PostnatalContactEntity::getSensitivityClass,
                PostnatalContactEntity::getConfidentialityCategory);
    }

    /**
     * The outcome of a recording attempt, saying WHICH of the two things happened.
     *
     * <p>Exists because a caller cannot tell a replay from a new visit by looking at the returned
     * row — both are a saved contact with an id. An HTTP surface must be able to: a CHW who syncs
     * twice needs the second attempt reported as the same visit, and a client that cannot distinguish
     * them either double-counts a woman's day-3 contact or treats a genuine second visit as noise.
     * Inferring it from timestamps or object identity would be a guess that is right most of the time,
     * which is the worst kind.
     */
    public record RecordOutcome(PostnatalContactEntity contact, boolean replayed) {
    }

    /** Record a contact, discarding whether it was a replay. */
    @Transactional
    public PostnatalContactEntity record(PostnatalContactEntity contact) {
        return recordReporting(contact).contact();
    }

    @Transactional
    public RecordOutcome recordReporting(PostnatalContactEntity contact) {
        if (contact.getClientOfflineId() != null && !contact.getClientOfflineId().isBlank()) {
            Optional<PostnatalContactEntity> replayed = contacts.findByTenantIdAndClientOfflineId(
                    contact.getTenantId(), contact.getClientOfflineId());
            if (replayed.isPresent()) {
                return new RecordOutcome(replayed.get(), true);
            }
        }
        if (contact.getPostnatalContactId() == null) {
            contact.setPostnatalContactId(UUID.randomUUID());
        }
        if (contact.getStatus() == null) {
            contact.setStatus("RECORDED");
        }
        if (contact.getRecordedAt() == null) {
            contact.setRecordedAt(OffsetDateTime.now());
        }
        var stamp = stampFor(contact);
        if (stamp == null) {
            // Not SRH content: ordinary maternity care, no category, no protection claimed. The
            // category trio is cleared rather than left alone, because a caller may hand back a
            // contact that once carried contraception content and no longer does. Leaving a stale
            // category beside FULL_CLINICAL would leave the row claiming to be SRH content it no
            // longer holds — and post-flip the shadow signal would keep counting it.
            contact.setSensitivityClass(ConfidentialityStamper.CLASS_FULL_CLINICAL);
            contact.setConfidentialityCategory(null);
            contact.setConfidentialityBasis(null);
            contact.setConfidentialityPolicyVersion(null);
        } else {
            contact.setSensitivityClass(stamp.sensitivityClass());
            contact.setConfidentialityCategory(stamp.category());
            contact.setConfidentialityBasis(stamp.basis());
            contact.setConfidentialityPolicyVersion(stamp.policyVersion());
        }
        // Booleans that carry a completed action default to false, so a null never reads as an
        // affirmative. screeningComplete is one of these: absent screening is FALSE, and the schema
        // then forbids a danger-sign finding — the screen must be positively asserted to be trusted.
        if (contact.getScreeningComplete() == null) {
            contact.setScreeningComplete(Boolean.FALSE);
        }
        if (contact.getLactationSupportGiven() == null) {
            contact.setLactationSupportGiven(Boolean.FALSE);
        }
        if (contact.getContraceptionDiscussed() == null) {
            contact.setContraceptionDiscussed(Boolean.FALSE);
        }
        if (contact.getReferralMade() == null) {
            contact.setReferralMade(Boolean.FALSE);
        }
        // dangerSignsPresent is deliberately NOT defaulted: if the screen was not completed it stays
        // null, which the read path renders as "not screened", never "no danger signs".
        return new RecordOutcome(contacts.save(contact), false);
    }
}
