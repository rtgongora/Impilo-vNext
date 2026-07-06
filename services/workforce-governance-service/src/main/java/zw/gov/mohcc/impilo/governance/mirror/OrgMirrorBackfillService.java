package zw.gov.mohcc.impilo.governance.mirror;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import zw.gov.mohcc.impilo.governance.persistence.OrganisationEntity;
import zw.gov.mohcc.impilo.governance.persistence.OrganisationRepository;

/**
 * Re-runnable backfill that sweeps all active {@code wgv_organisation} rows and
 * pushes each to the organization-registry mirror. Because the receiver upserts
 * idempotently on the wgv id, running this repeatedly converges the mirror
 * without creating duplicates.
 *
 * <p>Paginated and bounded by {@code impilo.org-mirror.backfill-page-size}, and
 * gated behind {@code impilo.org-mirror.enabled}: when the producer is disabled
 * the sweep is a no-op that reports {@code enabled=false} and zero pushes.
 * Failures are per-row and non-fatal — one bad push does not abort the sweep.
 */
@Service
public class OrgMirrorBackfillService {

    private static final Logger log = LoggerFactory.getLogger(OrgMirrorBackfillService.class);

    private final OrganisationRepository organisationRepository;
    private final OrgRegistryMirrorClient mirrorClient;
    private final OrgMirrorProperties properties;

    public OrgMirrorBackfillService(OrganisationRepository organisationRepository,
                                    OrgRegistryMirrorClient mirrorClient,
                                    OrgMirrorProperties properties) {
        this.organisationRepository = organisationRepository;
        this.mirrorClient = mirrorClient;
        this.properties = properties;
    }

    public BackfillResult backfill(UUID correlationId) {
        if (!properties.isEnabled()) {
            log.info("Org mirror backfill requested but producer is disabled (impilo.org-mirror.enabled=false) — no-op");
            return new BackfillResult(false, 0, 0, 0, 0);
        }
        int pageSize = Math.max(1, properties.getBackfillPageSize());
        long pushed = 0;
        long failed = 0;
        long total = 0;
        int pageIndex = 0;
        Page<OrganisationEntity> page;
        do {
            page = organisationRepository.findByActiveFlagTrueOrderByCreatedAtAsc(PageRequest.of(pageIndex, pageSize));
            for (OrganisationEntity org : page.getContent()) {
                total++;
                boolean ok = mirrorClient.push(OrgMirrorPayload.from(org), org.getTenantId(), correlationId);
                if (ok) {
                    pushed++;
                } else {
                    failed++;
                }
            }
            pageIndex++;
        } while (page.hasNext());
        log.info("Org mirror backfill complete: total={} pushed={} failed={}", total, pushed, failed);
        return new BackfillResult(true, total, pushed, failed, 0);
    }

    /**
     * @param enabled whether the mirror producer was enabled (false ⇒ no-op sweep)
     * @param total   active organisations examined
     * @param pushed  rows successfully mirrored
     * @param failed  rows whose mirror push failed
     * @param skipped rows skipped (reserved; currently always 0 while producer disabled skips all via {@code enabled=false})
     */
    public record BackfillResult(boolean enabled, long total, long pushed, long failed, long skipped) {
    }
}
