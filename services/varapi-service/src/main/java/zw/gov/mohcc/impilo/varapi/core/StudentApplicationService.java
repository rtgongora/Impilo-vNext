package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ApplicationContributorInviteEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ApplicationSectionEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ApplicationContributorInviteRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ApplicationSectionRepository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The student registration application: sections completed over time, a bounded contribution from
 * the training institution, and a regulator who returns work rather than rejecting people
 * (NCZ-W1C).
 *
 * <p><b>The database stops a bad invite; this class stops a bad read.</b> V040 refuses an invite
 * that names no section, wildcards them, or names one the application does not have. None of that
 * helps if a contributor endpoint then loads the whole application and trusts the caller to look
 * only at their part. Every contributor read here is filtered through the invite's own scope, so
 * the sections outside it are never assembled in the first place — not hidden, not filtered at the
 * edge, absent.</p>
 */
@Service
public class StudentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(StudentApplicationService.class);

    /** Statuses in which a contributor may still act. An expired invite is not a quiet no-op. */
    private static final Set<String> ACTIVE_INVITE_STATUSES = Set.of("INVITED", "ACCEPTED");

    private final ApplicationSectionRepository sectionRepository;
    private final ApplicationContributorInviteRepository inviteRepository;

    public StudentApplicationService(ApplicationSectionRepository sectionRepository,
                                     ApplicationContributorInviteRepository inviteRepository) {
        this.sectionRepository = sectionRepository;
        this.inviteRepository = inviteRepository;
    }

    // ── The applicant's own view ─────────────────────────────────────────────────────────────

    public List<ApplicationSectionEntity> sectionsForApplicant(UUID tenantId, Long applicationId) {
        return sectionRepository.findByTenantIdAndApplicationIdOrderBySequenceNoAsc(
                tenantId, applicationId);
    }

    // ── The contributor's view: bounded by construction ──────────────────────────────────────

    /**
     * What a contributor may see. Not the application with parts hidden — only the sections their
     * invite names, loaded by key, so there is no moment at which the rest exists in the response
     * and depends on a later filter to stay out of it.
     */
    @Transactional(readOnly = true)
    public List<ApplicationSectionEntity> sectionsForContributor(UUID tenantId, Long inviteId) {
        ApplicationContributorInviteEntity invite = requireActiveInvite(tenantId, inviteId);
        List<String> scope = Arrays.asList(invite.getScopeSections());
        return sectionRepository.findByTenantIdAndApplicationIdAndSectionKeyInOrderBySequenceNoAsc(
                tenantId, invite.getApplicationId(), scope);
    }

    /**
     * A contributor completing their part. The section is checked against the invite's scope before
     * anything is written, because an invite that grants "enrolment" must not become a way to write
     * "identity" by passing a different key.
     */
    @Transactional
    public ApplicationSectionEntity contributorCompletesSection(
            UUID tenantId, Long inviteId, String sectionKey,
            com.fasterxml.jackson.databind.JsonNode content) {

        ApplicationContributorInviteEntity invite = requireActiveInvite(tenantId, inviteId);
        assertWithinScope(invite, sectionKey);

        ApplicationSectionEntity section = sectionRepository
                .findByTenantIdAndApplicationIdAndSectionKey(
                        tenantId, invite.getApplicationId(), sectionKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No section " + sectionKey + " on this application"));

        section.setContent(content);
        section.setState("COMPLETE");
        section.setCompletedAt(Instant.now());
        section.setUpdatedAt(Instant.now());
        ApplicationSectionEntity saved = sectionRepository.save(section);

        invite.setStatus("COMPLETED");
        invite.setCompletedAt(Instant.now());
        invite.setUpdatedAt(Instant.now());
        inviteRepository.save(invite);
        return saved;
    }

    /**
     * The check that keeps a bounded contribution bounded. Kept separate and named so it is
     * conspicuous: any future contributor write path that does not call it is the bug.
     */
    void assertWithinScope(ApplicationContributorInviteEntity invite, String sectionKey) {
        boolean inScope = invite.getScopeSections() != null
                && Arrays.asList(invite.getScopeSections()).contains(sectionKey);
        if (!inScope) {
            // Deliberately does not name the sections that DO exist: a contributor probing for the
            // shape of someone's application should learn nothing from the refusal.
            log.warn("Contributor invite {} attempted section {} outside its scope",
                    invite.getId(), sectionKey);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This contribution does not cover that part of the application.");
        }
    }

    private ApplicationContributorInviteEntity requireActiveInvite(UUID tenantId, Long inviteId) {
        ApplicationContributorInviteEntity invite = inviteRepository
                .findByTenantIdAndId(tenantId, inviteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No contributor invitation " + inviteId));

        if (!ACTIVE_INVITE_STATUSES.contains(invite.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This contribution is " + invite.getStatus().toLowerCase() + " and can no "
                            + "longer be used.");
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            // Expiry is enforced on READ, not only by a sweep. A sweep that has not run yet must
            // not leave an expired invitation working.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This contribution request has expired.");
        }
        return invite;
    }

    // ── The regulator returning work ─────────────────────────────────────────────────────────

    /**
     * Return one section for correction. The application does not move: V040's trigger refuses a
     * return before submission and preserves the case, and this keeps the reason mandatory at the
     * service boundary too, so a caller gets a 400 rather than a constraint violation.
     */
    @Transactional
    public ApplicationSectionEntity returnSection(UUID tenantId, Long applicationId,
                                                  String sectionKey, String reason, String by) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Say what needs to change. A section returned without a reason leaves the "
                            + "applicant guessing at a form they have already completed.");
        }
        ApplicationSectionEntity section = sectionRepository
                .findByTenantIdAndApplicationIdAndSectionKey(tenantId, applicationId, sectionKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No section " + sectionKey + " on application " + applicationId));

        section.setState("RETURNED");
        section.setReturnedReason(reason);
        section.setReturnedBy(by);
        section.setUpdatedAt(Instant.now());
        return sectionRepository.save(section);
    }

    /**
     * The applicant answering a return. Only the returned section reopens — everything else they
     * already completed stays completed, which is the difference between correcting a document and
     * re-applying.
     */
    @Transactional
    public ApplicationSectionEntity resubmitSection(
            UUID tenantId, Long applicationId, String sectionKey,
            com.fasterxml.jackson.databind.JsonNode content) {

        ApplicationSectionEntity section = sectionRepository
                .findByTenantIdAndApplicationIdAndSectionKey(tenantId, applicationId, sectionKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No section " + sectionKey + " on application " + applicationId));

        if (!"RETURNED".equals(section.getState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That section has not been returned, so there is nothing to resubmit.");
        }
        section.setContent(content);
        section.setState("COMPLETE");
        section.setCompletedAt(Instant.now());
        section.setUpdatedAt(Instant.now());
        // returnedReason is kept: the history of what was asked for survives the correction.
        return sectionRepository.save(section);
    }

    /** Whether every section is complete — the precondition a regulator's decision depends on. */
    @Transactional(readOnly = true)
    public boolean isComplete(UUID tenantId, Long applicationId) {
        return sectionRepository.countByTenantIdAndApplicationIdAndStateNot(
                tenantId, applicationId, "COMPLETE") == 0;
    }
}
