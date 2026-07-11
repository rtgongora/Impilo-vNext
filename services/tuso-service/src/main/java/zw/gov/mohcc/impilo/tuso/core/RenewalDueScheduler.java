package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCertificateEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCertificateStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityStatusHistoryEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCertificateRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityStatusHistoryRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renewal and expiry housekeeping (rule-configured window; HPA-2017-V1 p.9).
 *
 * <p>This job only MARKS state and raises work — {@code REGISTERED_ACTIVE →
 * RENEWAL_DUE} inside the configured window, and {@code ACTIVE → EXPIRED}
 * certificates past their expiry date. It never suspends, revokes or closes a
 * facility: those remain explicit, audited human decisions.</p>
 */
@Service
public class RenewalDueScheduler {

    private static final Logger log = LoggerFactory.getLogger(RenewalDueScheduler.class);

    private final FacilityCertificateRepository certificateRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityStatusHistoryRepository statusHistoryRepository;
    private final EventOutboxRepository outboxRepository;
    private final RegulatoryRuleService ruleService;

    public RenewalDueScheduler(FacilityCertificateRepository certificateRepository,
                               FacilityRepository facilityRepository,
                               FacilityStatusHistoryRepository statusHistoryRepository,
                               EventOutboxRepository outboxRepository,
                               RegulatoryRuleService ruleService) {
        this.certificateRepository = certificateRepository;
        this.facilityRepository = facilityRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.outboxRepository = outboxRepository;
        this.ruleService = ruleService;
    }

    @Scheduled(cron = "${tuso.regulatory.renewal-sweep-cron:0 30 2 * * *}")
    @Transactional
    public void sweep() {
        int marked = markRenewalDue();
        int expired = markExpiredCertificates();
        if (marked > 0 || expired > 0) {
            log.info("Renewal sweep: {} facility(ies) marked RENEWAL_DUE, {} certificate(s) expired", marked, expired);
        }
    }

    int markRenewalDue() {
        LocalDate windowEnd = LocalDate.now().plusDays(ruleService.renewalDueWindowDays());
        List<FacilityCertificateEntity> expiring = certificateRepository
                .findByExpiryDateBetweenAndStatus(LocalDate.now(), windowEnd, FacilityCertificateStatus.ACTIVE);
        int marked = 0;
        for (FacilityCertificateEntity certificate : expiring) {
            FacilityEntity facility = certificate.getFacility();
            if (facility.getRegulatoryStatus() != FacilityRegulatoryStatus.REGISTERED_ACTIVE) {
                continue;
            }
            facility.setRegulatoryStatus(FacilityRegulatoryStatus.RENEWAL_DUE);
            facility.setRegulatoryStatusUpdatedAt(Instant.now());
            facilityRepository.save(facility);

            FacilityStatusHistoryEntity history = new FacilityStatusHistoryEntity();
            history.setFacility(facility);
            history.setRegulatoryStatus(FacilityRegulatoryStatus.RENEWAL_DUE);
            history.setChangedBy("renewal-scheduler");
            history.setAuthorityContext("SYSTEM");
            history.setReason("Certificate " + certificate.getCertificateNumber()
                    + " expires " + certificate.getExpiryDate());
            statusHistoryRepository.save(history);

            publish(facility.getId(), certificate.getTenantId(), "tuso.facility.renewal.due", Map.of(
                    "facilityId", facility.getId(),
                    "certificateId", certificate.getCertificateId().toString(),
                    "certificateNumber", certificate.getCertificateNumber(),
                    "expiryDate", certificate.getExpiryDate().toString()));
            marked++;
        }
        return marked;
    }

    int markExpiredCertificates() {
        List<FacilityCertificateEntity> lapsed = certificateRepository
                .findByExpiryDateBetweenAndStatus(LocalDate.now().minusYears(5),
                        LocalDate.now().minusDays(1), FacilityCertificateStatus.ACTIVE);
        int expired = 0;
        for (FacilityCertificateEntity certificate : lapsed) {
            certificate.setStatus(FacilityCertificateStatus.EXPIRED);
            certificate.setStatusReason("Expiry date " + certificate.getExpiryDate() + " passed");
            certificateRepository.save(certificate);
            publish(certificate.getFacility().getId(), certificate.getTenantId(),
                    "tuso.facility.certificate.expired", Map.of(
                            "facilityId", certificate.getFacility().getId(),
                            "certificateId", certificate.getCertificateId().toString(),
                            "certificateNumber", certificate.getCertificateNumber()));
            expired++;
        }
        return expired;
    }

    private void publish(Long facilityId, UUID tenantId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FACILITY");
        event.setAggregateId(String.valueOf(facilityId));
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setTenantId(tenantId != null ? tenantId.toString() : null);
        event.setProducer("tuso");
        event.setSubjectType("Facility");
        event.setSubjectId(String.valueOf(facilityId));
        event.setPartitionKey(String.valueOf(facilityId));
        event.setSchemaVersion(1);
        event.setPodId("national-spine");
        event.setIdempotencyKey("tuso:renewal:" + eventType + ":" + facilityId + ":" + LocalDate.now());
        outboxRepository.save(event);
    }
}
