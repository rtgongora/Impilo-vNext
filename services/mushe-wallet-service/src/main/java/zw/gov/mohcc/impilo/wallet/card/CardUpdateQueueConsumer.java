package zw.gov.mohcc.impilo.wallet.card;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardUpdateQueueEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.CardUpdateQueueRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * B2: drains {@code card_update_queue} (PENDING) and writes each encrypted card
 * update onto the physical card through the {@link NfcCardWriter} seam — the
 * consumer that closes the physical-write pipeline the producer (B1 PHR sync) had
 * no counterpart for.
 *
 * <p>Per entry: attempt the APDU write; on success mark COMPLETED (via
 * {@link CardHealthDataService#markSyncedToCard}); on failure increment attempts and
 * mark FAILED past {@code max-attempts} so a poison payload can't spin forever. A
 * scheduled tick drains a bounded batch; the drain method is also callable on demand
 * (ops endpoint / test). The scheduler is opt-in so it never fires inside tests.</p>
 */
@Service
public class CardUpdateQueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(CardUpdateQueueConsumer.class);

    private final CardUpdateQueueRepository queueRepository;
    private final CardHealthDataService healthDataService;
    private final NfcCardWriter nfcCardWriter;
    private final int batchSize;
    private final int maxAttempts;

    public CardUpdateQueueConsumer(CardUpdateQueueRepository queueRepository,
                                   CardHealthDataService healthDataService,
                                   NfcCardWriter nfcCardWriter,
                                   @Value("${mushe.card-sync.drain.batch-size:50}") int batchSize,
                                   @Value("${mushe.card-sync.drain.max-attempts:5}") int maxAttempts) {
        this.queueRepository = queueRepository;
        this.healthDataService = healthDataService;
        this.nfcCardWriter = nfcCardWriter;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /** Scheduled drain — opt-in via {@code mushe.card-sync.scheduler.enabled=true}. */
    @Scheduled(fixedDelayString = "${mushe.card-sync.drain.fixed-delay-ms:30000}")
    public void scheduledDrain() {
        if (!schedulerEnabled) {
            return;
        }
        drainPending();
    }

    @Value("${mushe.card-sync.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    /** Result of one drain pass. */
    public record DrainResult(int processed, int synced, int failed, int retried) {}

    /**
     * Drain a bounded batch of PENDING updates. Each entry is handled in its own
     * transaction so one failure doesn't roll back the rest.
     */
    public DrainResult drainPending() {
        List<CardUpdateQueueEntity> batch = queueRepository.findByStatusOrderByPriorityAscCreatedAtAsc(
                "PENDING", PageRequest.of(0, batchSize));
        int synced = 0, failed = 0, retried = 0;
        for (CardUpdateQueueEntity entry : batch) {
            Outcome o = handleOne(entry.getId());
            switch (o) {
                case SYNCED -> synced++;
                case FAILED -> failed++;
                case RETRY -> retried++;
            }
        }
        if (!batch.isEmpty()) {
            log.info("Card-sync drain: processed={} synced={} failed={} retried={}",
                    batch.size(), synced, failed, retried);
        }
        return new DrainResult(batch.size(), synced, failed, retried);
    }

    private enum Outcome { SYNCED, RETRY, FAILED }

    // Each entry is handled independently: markSyncedToCard and the queue saves each
    // run in their own Spring Data transaction, and this try/catch isolates a failure
    // so one bad payload never aborts the batch.
    private Outcome handleOne(Long id) {
        CardUpdateQueueEntity entry = queueRepository.findById(id).orElse(null);
        if (entry == null || !"PENDING".equals(entry.getStatus())) {
            return Outcome.RETRY; // already claimed/handled by another pass
        }
        entry.setAttempts(entry.getAttempts() == null ? 1 : entry.getAttempts() + 1);
        entry.setLastAttemptAt(OffsetDateTime.now());
        try {
            NfcCardWriter.WriteReceipt receipt = nfcCardWriter.write(
                    entry.getCardId(), entry.getUpdateType(), entry.getPayloadEncrypted());
            if (receipt.ok()) {
                // markSyncedToCard flips status→COMPLETED and stamps the card's sync time.
                healthDataService.markSyncedToCard(entry.getQueueId());
                return Outcome.SYNCED;
            }
            return failOrRetry(entry, "writer status " + receipt.status());
        } catch (RuntimeException e) {
            return failOrRetry(entry, e.getMessage());
        }
    }

    private Outcome failOrRetry(CardUpdateQueueEntity entry, String reason) {
        if (entry.getAttempts() >= maxAttempts) {
            entry.setStatus("FAILED");
            queueRepository.save(entry);
            log.warn("Card-sync entry {} FAILED after {} attempts: {}",
                    entry.getQueueId(), entry.getAttempts(), reason);
            return Outcome.FAILED;
        }
        queueRepository.save(entry); // stays PENDING for the next pass
        log.warn("Card-sync entry {} write failed (attempt {}), will retry: {}",
                entry.getQueueId(), entry.getAttempts(), reason);
        return Outcome.RETRY;
    }
}
