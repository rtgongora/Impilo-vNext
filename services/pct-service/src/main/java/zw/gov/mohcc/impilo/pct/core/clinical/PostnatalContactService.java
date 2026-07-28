package zw.gov.mohcc.impilo.pct.core.clinical;

import org.springframework.stereotype.Service;
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

    public PostnatalContactService(PostnatalContactRepository contacts) {
        this.contacts = contacts;
    }

    @Transactional(readOnly = true)
    public List<PostnatalContactEntity> forMother(UUID tenantId, String motherCpid) {
        if (tenantId == null || motherCpid == null || motherCpid.isBlank()) {
            return List.of();
        }
        return contacts.findByMother(tenantId, motherCpid);
    }

    @Transactional
    public PostnatalContactEntity record(PostnatalContactEntity contact) {
        if (contact.getClientOfflineId() != null && !contact.getClientOfflineId().isBlank()) {
            Optional<PostnatalContactEntity> replayed = contacts.findByTenantIdAndClientOfflineId(
                    contact.getTenantId(), contact.getClientOfflineId());
            if (replayed.isPresent()) {
                return replayed.get();
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
        if (contact.getSensitivityClass() == null) {
            contact.setSensitivityClass("FULL_CLINICAL");
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
        return contacts.save(contact);
    }
}
