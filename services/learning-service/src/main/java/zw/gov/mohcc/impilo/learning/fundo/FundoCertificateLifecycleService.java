package zw.gov.mohcc.impilo.learning.fundo;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.CertificateEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CertificateRepository;

/**
 * Scheduled certificate renewal/refresher/expiry lifecycle.
 *
 * <p>Two idempotent sweeps run on a fixed delay:
 * <ul>
 *   <li><b>Refresher</b> — active certificates entering the pre-expiry lead window
 *       emit {@code certificate.refresher.due.v1}, record a learner notification
 *       intent, and are marked {@code refresher_notified_at} (dedupe).</li>
 *   <li><b>Expiry</b> — active certificates past {@code valid_until} transition
 *       {@code ISSUED -> EXPIRED}, emit {@code certificate.expired.v1}, and record a
 *       renewal notification intent. The status flip is the natural dedupe.</li>
 * </ul>
 *
 * <p>This is the engine behind the renewal/refresher/expiry journey. It feeds the
 * Vashandi workforce-readiness summary (expired certs ⇒ NOT_READY) and the honest
 * notification adapter (no fake delivery — only PENDING intents are recorded).
 * Disabled-by-default-able via {@code learning.lifecycle.certificate.enabled}.
 */
@Service
public class FundoCertificateLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(FundoCertificateLifecycleService.class);
    private static final String ACTIVE = "ISSUED";
    private static final String EXPIRED = "EXPIRED";
    private static final int BATCH = 200;

    private final CertificateRepository certificateRepository;
    private final FundoOutboxAppender outbox;
    private final FundoNotificationIntentWriter notifications;
    private final boolean enabled;
    private final long refresherLeadDays;

    public FundoCertificateLifecycleService(
            CertificateRepository certificateRepository,
            FundoOutboxAppender outbox,
            FundoNotificationIntentWriter notifications,
            @Value("${learning.lifecycle.certificate.enabled:true}") boolean enabled,
            @Value("${learning.lifecycle.certificate.refresher-lead-days:30}") long refresherLeadDays) {
        this.certificateRepository = certificateRepository;
        this.outbox = outbox;
        this.notifications = notifications;
        this.enabled = enabled;
        this.refresherLeadDays = refresherLeadDays;
    }

    @Scheduled(
            initialDelayString = "${learning.lifecycle.certificate.initial-delay-ms:60000}",
            fixedDelayString = "${learning.lifecycle.certificate.poll-interval-ms:3600000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        try {
            int refreshed = runRefresherSweep();
            int expired = runExpirySweep();
            if (refreshed > 0 || expired > 0) {
                log.info("Certificate lifecycle sweep: {} refresher-due, {} expired", refreshed, expired);
            }
        } catch (Exception e) {
            log.warn("Certificate lifecycle sweep failed: {}", e.getMessage());
        }
    }

    /** Emit a one-time pre-expiry refresher signal per certificate. */
    @Transactional
    public int runRefresherSweep() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime windowEnd = now.plusDays(refresherLeadDays);
        List<CertificateEntity> due = certificateRepository
                .findByStatusAndRefresherNotifiedAtIsNullAndValidUntilNotNullAndValidUntilBetween(
                        ACTIVE, now, windowEnd, PageRequest.of(0, BATCH));
        for (CertificateEntity c : due) {
            Map<String, Object> payload = basePayload(c);
            payload.put("validUntil", c.getValidUntil().toString());
            payload.put("refresherLeadDays", refresherLeadDays);
            outbox.append("certificate", c.getId().toString(),
                    FundoNativeEventTypes.CERTIFICATE_REFRESHER_DUE, payload);
            notifications.record(
                    c.getTenantId(), c.getSubjectType(), c.getSubjectId(),
                    "learning.certificate.refresher.due",
                    "Refresher due soon: " + safe(c.getTitle()),
                    "Your certificate \"" + safe(c.getTitle()) + "\" expires on "
                            + c.getValidUntil() + ". Complete the refresher to stay current.",
                    "IN_APP", c.getValidUntil(), payload);
            c.setRefresherNotifiedAt(now);
            certificateRepository.save(c);
        }
        return due.size();
    }

    /** Transition lapsed certificates to EXPIRED and record a renewal intent. */
    @Transactional
    public int runExpirySweep() {
        OffsetDateTime now = OffsetDateTime.now();
        List<CertificateEntity> lapsed = certificateRepository
                .findByStatusAndValidUntilNotNullAndValidUntilBefore(ACTIVE, now, PageRequest.of(0, BATCH));
        for (CertificateEntity c : lapsed) {
            c.setStatus(EXPIRED);
            certificateRepository.save(c);
            Map<String, Object> payload = basePayload(c);
            payload.put("validUntil", c.getValidUntil().toString());
            payload.put("expiredAt", now.toString());
            outbox.append("certificate", c.getId().toString(),
                    FundoNativeEventTypes.CERTIFICATE_EXPIRED, payload);
            notifications.record(
                    c.getTenantId(), c.getSubjectType(), c.getSubjectId(),
                    "learning.certificate.expired",
                    "Certificate expired: " + safe(c.getTitle()),
                    "Your certificate \"" + safe(c.getTitle()) + "\" expired on "
                            + c.getValidUntil() + ". Renew it to restore readiness.",
                    "IN_APP", null, payload);
        }
        return lapsed.size();
    }

    private static Map<String, Object> basePayload(CertificateEntity c) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("certificateId", c.getId().toString());
        p.put("tenantId", c.getTenantId().toString());
        p.put("subjectType", c.getSubjectType());
        p.put("subjectId", c.getSubjectId());
        p.put("courseId", c.getCourseId() == null ? null : c.getCourseId().toString());
        p.put("title", c.getTitle());
        return p;
    }

    private static String safe(String s) {
        return s == null ? "certificate" : s;
    }
}
