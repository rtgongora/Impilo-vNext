package zw.gov.mohcc.impilo.varapi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.persistence.entity.*;
import zw.gov.mohcc.impilo.varapi.persistence.repository.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Two-way application correspondence (ROM-W4, R7): the RFI request/respond/close loop and the
 * case-message thread with an APPLICANT-vs-INTERNAL visibility split. Internal assessments live in
 * the same case but never appear in the applicant view.
 *
 * <p><b>Self-regulation firewall (doctrine §4.4):</b> a regulator SHALL NOT act on their OWN
 * application. Every regulator-side action asserts the acting person's Health-ID is not the
 * application's subject; a self-action is refused with {@link RecusalRequiredException}. The tie is
 * detected on the person Health-ID, never the provider identifier alone.</p>
 */
@Service
public class RegulatoryApplicationCorrespondenceService {

    /** Thrown when a regulator attempts to act on their own application. */
    public static class RecusalRequiredException extends RuntimeException {
        public RecusalRequiredException() {
            super("You cannot act as a regulator on your own application (recusal required).");
        }
    }

    private final ProviderApplicationRepository applicationRepository;
    private final ApplicationInformationRequestRepository rfiRepository;
    private final ApplicationCaseMessageRepository messageRepository;

    public RegulatoryApplicationCorrespondenceService(ProviderApplicationRepository applicationRepository,
                                                      ApplicationInformationRequestRepository rfiRepository,
                                                      ApplicationCaseMessageRepository messageRepository) {
        this.applicationRepository = applicationRepository;
        this.rfiRepository = rfiRepository;
        this.messageRepository = messageRepository;
    }

    private ProviderApplicationEntity require(UUID tenantId, Long applicationId) {
        return applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
    }

    /** Recusal guard: the acting regulator must not be the application's subject person. */
    void assertNotSelf(ProviderApplicationEntity application, String actorHealthId) {
        ProviderEntity subject = application.getProvider();
        if (subject != null && subject.getImpiloHealthId() != null && actorHealthId != null
                && subject.getImpiloHealthId().toString().equalsIgnoreCase(actorHealthId.trim())) {
            throw new RecusalRequiredException();
        }
    }

    /** Regulator opens an RFI (correction/clarification request). Recusal-guarded. */
    @Transactional
    public ApplicationInformationRequestEntity openRfi(UUID tenantId, String actorHealthId,
                                                       Long applicationId, String message, LocalDate dueDate) {
        ProviderApplicationEntity app = require(tenantId, applicationId);
        assertNotSelf(app, actorHealthId);
        ApplicationInformationRequestEntity rfi = new ApplicationInformationRequestEntity();
        rfi.setTenantId(tenantId);
        rfi.setApplicationId(applicationId);
        rfi.setRequestedBy(actorHealthId);
        rfi.setMessage(message);
        rfi.setDueDate(dueDate);
        rfi.setStatus("OPEN");
        return rfiRepository.save(rfi);
    }

    /** Applicant responds to an RFI. */
    @Transactional
    public ApplicationInformationRequestEntity respondRfi(UUID tenantId, String actorHealthId,
                                                          Long rfiId, String responseNotes) {
        ApplicationInformationRequestEntity rfi = rfiRepository.findById(rfiId)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("RFI not found: " + rfiId));
        rfi.setStatus("RESPONDED");
        rfi.setResponseNotes(responseNotes);
        rfi.setRespondedAt(OffsetDateTime.now());
        rfi.setRespondedBy(actorHealthId);
        return rfiRepository.save(rfi);
    }

    /** Regulator closes an RFI. Recusal-guarded. */
    @Transactional
    public ApplicationInformationRequestEntity closeRfi(UUID tenantId, String actorHealthId, Long rfiId) {
        ApplicationInformationRequestEntity rfi = rfiRepository.findById(rfiId)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("RFI not found: " + rfiId));
        assertNotSelf(require(tenantId, rfi.getApplicationId()), actorHealthId);
        rfi.setStatus("CLOSED");
        rfi.setClosedAt(OffsetDateTime.now());
        rfi.setClosedBy(actorHealthId);
        return rfiRepository.save(rfi);
    }

    /**
     * Post a case message. A regulator OUTBOUND/INTERNAL message is recusal-guarded; an APPLICANT
     * INTERNAL message is impossible (internal is a regulator-only lane).
     */
    @Transactional
    public ApplicationCaseMessageEntity postMessage(UUID tenantId, String actorHealthId, Long applicationId,
                                                    String direction, String visibility, String capacity, String body) {
        ProviderApplicationEntity app = require(tenantId, applicationId);
        boolean regulatorSide = "OUTBOUND".equalsIgnoreCase(direction) || "INTERNAL".equalsIgnoreCase(visibility);
        if (regulatorSide) {
            assertNotSelf(app, actorHealthId);
        }
        ApplicationCaseMessageEntity m = new ApplicationCaseMessageEntity();
        m.setTenantId(tenantId);
        m.setApplicationId(applicationId);
        m.setDirection("INBOUND".equalsIgnoreCase(direction) ? "INBOUND" : "OUTBOUND");
        m.setVisibility("INTERNAL".equalsIgnoreCase(visibility) ? "INTERNAL" : "APPLICANT");
        m.setAuthorActorId(actorHealthId);
        m.setAuthorCapacity(capacity);
        m.setBody(body);
        return messageRepository.save(m);
    }

    /** The applicant's view — APPLICANT-visibility messages only. INTERNAL never appears here. */
    @Transactional(readOnly = true)
    public List<ApplicationCaseMessageEntity> applicantThread(UUID tenantId, Long applicationId) {
        return messageRepository.findByTenantIdAndApplicationIdAndVisibilityOrderByCreatedAtAsc(
                tenantId, applicationId, "APPLICANT");
    }

    /** The regulator's view — all messages including internal notes. */
    @Transactional(readOnly = true)
    public List<ApplicationCaseMessageEntity> fullThread(UUID tenantId, Long applicationId) {
        return messageRepository.findByTenantIdAndApplicationIdOrderByCreatedAtAsc(tenantId, applicationId);
    }

    @Transactional(readOnly = true)
    public List<ApplicationInformationRequestEntity> rfis(UUID tenantId, Long applicationId) {
        return rfiRepository.findByTenantIdAndApplicationIdOrderByCreatedAtDesc(tenantId, applicationId);
    }
}
