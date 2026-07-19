package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.enums.ProviderLifecycleStatus;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.LicenseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Advances the provider licence-renewal FSM as licences approach and pass expiry — the states
 * LICENCE_DUE_FOR_RENEWAL and LAPSED were defined in the enum but never set by any code (R2/G8).
 *
 * <p>Runs outside a request (no TrustContext), so it writes the canonical lifecycle_status directly
 * and recomputes the derived axes via {@link ProviderEntity#deriveStatusProjections()} — it does not
 * go through the request-scoped ProviderService transition path. Idempotent: only providers not
 * already in the target state are moved, so re-running is a no-op.</p>
 */
@Component
public class LicenceRenewalSweep {

    private static final Logger log = LoggerFactory.getLogger(LicenceRenewalSweep.class);

    /** Default DUE window; no per-council config field exists yet (JSONB override deferred). */
    private static final int RENEWAL_WINDOW_DAYS = 60;

    private static final Set<ProviderLifecycleStatus> LAPSABLE = EnumSet.of(
            ProviderLifecycleStatus.LICENCED_ACTIVE,
            ProviderLifecycleStatus.LICENCE_DUE_FOR_RENEWAL,
            ProviderLifecycleStatus.RENEWAL_IN_PROGRESS);

    private final LicenseRepository licenseRepository;
    private final ProviderRepository providerRepository;
    private final EventOutboxRepository outboxRepository;

    public LicenceRenewalSweep(LicenseRepository licenseRepository,
                               ProviderRepository providerRepository,
                               EventOutboxRepository outboxRepository) {
        this.licenseRepository = licenseRepository;
        this.providerRepository = providerRepository;
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(fixedDelayString = "${varapi.licence-sweep.interval-ms:3600000}",
            initialDelayString = "${varapi.licence-sweep.initial-delay-ms:60000}")
    @Transactional
    public void sweep() {
        LocalDate today = LocalDate.now();
        int due = markDueForRenewal(today, today.plusDays(RENEWAL_WINDOW_DAYS));
        int lapsed = markLapsed(today);
        if (due > 0 || lapsed > 0) {
            log.info("Licence renewal sweep: {} due-for-renewal, {} lapsed", due, lapsed);
        }
    }

    /** Testable unit: move ACTIVE, LICENCED_ACTIVE providers whose licence expires in [from,to] to DUE. */
    @Transactional
    public int markDueForRenewal(LocalDate from, LocalDate to) {
        int count = 0;
        List<LicenseEntity> expiring = licenseRepository.findByStatusAndValidToBetween("ACTIVE", from, to);
        for (LicenseEntity licence : expiring) {
            ProviderEntity provider = licence.getProvider();
            if (provider == null) continue;
            if (parse(provider.getLifecycleStatus()) == ProviderLifecycleStatus.LICENCED_ACTIVE) {
                transition(provider, ProviderLifecycleStatus.LICENCE_DUE_FOR_RENEWAL, "varapi.provider.licence_due",
                        licence.getValidTo());
                count++;
            }
        }
        return count;
    }

    /** Testable unit: move providers whose active licence is already expired to LAPSED. */
    @Transactional
    public int markLapsed(LocalDate asOf) {
        int count = 0;
        List<LicenseEntity> expired = licenseRepository.findByStatusAndValidToBefore("ACTIVE", asOf);
        for (LicenseEntity licence : expired) {
            ProviderEntity provider = licence.getProvider();
            if (provider == null) continue;
            if (LAPSABLE.contains(parse(provider.getLifecycleStatus()))) {
                transition(provider, ProviderLifecycleStatus.LAPSED, "varapi.provider.lapsed", licence.getValidTo());
                count++;
            }
        }
        return count;
    }

    private void transition(ProviderEntity provider, ProviderLifecycleStatus target, String eventType, LocalDate validTo) {
        String previous = provider.getLifecycleStatus();
        provider.setLifecycleStatus(target.name());
        provider.deriveStatusProjections();
        provider.setVersion((provider.getVersion() == null ? 0 : provider.getVersion()) + 1);
        provider.setUpdatedBy("SYSTEM_LICENCE_SWEEP");
        providerRepository.save(provider);

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("PROVIDER");
        event.setAggregateId(provider.getProviderPublicId());
        event.setEventType(eventType);
        // impiloHealthId = the claimed person anchor, so trust-plane consumers can
        // tear down the person's active sessions/tokens on revocation (W1 D-P3).
        // Unclaimed profiles carry a synthetic anchor — emit empty, never the
        // synthetic UUID, so consumers cannot revoke an unrelated actor.
        java.util.UUID personAnchor = provider.getClaimedHealthId();
        event.setPayload(String.format(
                "{\"providerPublicId\":\"%s\",\"impiloHealthId\":\"%s\",\"previousLifecycle\":\"%s\",\"newLifecycle\":\"%s\",\"licenceValidTo\":\"%s\"}",
                provider.getProviderPublicId(), personAnchor != null ? personAnchor : "",
                previous, target.name(), validTo != null ? validTo : ""));
        outboxRepository.save(event);
    }

    private static ProviderLifecycleStatus parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ProviderLifecycleStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
