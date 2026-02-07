package zw.gov.mohcc.impilo.shareslip.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.ShareAuditEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.ShareLinkEntity;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.ShareAuditRepository;
import zw.gov.mohcc.impilo.shareslip.persistence.repository.ShareLinkRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled job to expire share links that have passed their expiry time.
 *
 * Runs every 60 seconds, finds all ACTIVE links with expiresAt in the past,
 * sets their status to EXPIRED, and records audit + outbox events.
 */
@Component
public class ShareExpiryService {

    private static final Logger log = LoggerFactory.getLogger(ShareExpiryService.class);

    private final ShareLinkRepository shareLinkRepository;
    private final ShareAuditRepository shareAuditRepository;
    private final EventOutboxRepository eventOutboxRepository;

    public ShareExpiryService(ShareLinkRepository shareLinkRepository,
                              ShareAuditRepository shareAuditRepository,
                              EventOutboxRepository eventOutboxRepository) {
        this.shareLinkRepository = shareLinkRepository;
        this.shareAuditRepository = shareAuditRepository;
        this.eventOutboxRepository = eventOutboxRepository;
    }

    @Scheduled(fixedDelayString = "${share-slip.expiry.poll-interval-ms:60000}")
    @Transactional
    public void expireOldLinks() {
        List<ShareLinkEntity> expired = shareLinkRepository.findExpiredActive(OffsetDateTime.now());

        if (expired.isEmpty()) {
            return;
        }

        log.info("Expiring {} share link(s)", expired.size());

        for (ShareLinkEntity entity : expired) {
            entity.setStatus("EXPIRED");
            shareLinkRepository.save(entity);

            // Audit
            ShareAuditEntity audit = new ShareAuditEntity();
            audit.setLinkId(entity.getLinkId());
            audit.setAction("EXPIRED");
            audit.setActorId("SYSTEM");
            audit.setDetails("{\"reason\":\"scheduled_expiry\"}");
            shareAuditRepository.save(audit);

            // Outbox event
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("SHARE_LINK");
            event.setAggregateId(entity.getLinkId().toString());
            event.setEventType("share.link.expired");
            event.setPayload(
                    "{\"linkId\":\"%s\",\"tenantId\":\"%s\",\"subjectType\":\"%s\",\"subjectId\":\"%s\",\"expiredAt\":\"%s\"}"
                            .formatted(entity.getLinkId(), entity.getTenantId(),
                                    entity.getSubjectType(), entity.getSubjectId(),
                                    OffsetDateTime.now())
            );
            eventOutboxRepository.save(event);

            log.debug("Expired share link: linkId={}", entity.getLinkId());
        }
    }
}
