package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.persistence.entity.ExternalSourceEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.ImportJobEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.ExternalSourceRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.ImportJobRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Drains the source-import job queue honestly. runSourceImport() enqueues
 * PENDING jobs for external sources, but pull-source execution (REST/KAFKA
 * connectors) is deliberately not implemented yet — so instead of leaving a
 * silent forever-queue, this worker FAILS those jobs with a machine-readable
 * UNSUPPORTED_SOURCE_MODE reason. When a real connector lands, its mode is
 * routed here before the fail-closed fallback.
 *
 * CSV uploads never reach this worker: importCsv() completes synchronously
 * and its jobs carry no source_id (the worker only claims sourced jobs).
 */
@Component
public class SourceImportWorker {

    private static final Logger log = LoggerFactory.getLogger(SourceImportWorker.class);
    private static final int BATCH = 50;

    private final ImportJobRepository jobRepository;
    private final ExternalSourceRepository sourceRepository;
    private final ObjectMapper objectMapper;

    public SourceImportWorker(ImportJobRepository jobRepository,
                              ExternalSourceRepository sourceRepository,
                              ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.sourceRepository = sourceRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${msika.import.worker-interval-ms:60000}")
    @Transactional
    public void processPendingSourceJobs() {
        List<ImportJobEntity> pending = jobRepository.findByStatus("PENDING", PageRequest.of(0, BATCH))
                .getContent();
        for (ImportJobEntity job : pending) {
            if (job.getSourceId() == null) {
                continue; // not a sourced job — never claimed by this worker
            }
            String mode = sourceRepository.findById(job.getSourceId())
                    .map(ExternalSourceEntity::getMode)
                    .orElse("UNKNOWN");
            failUnsupported(job, mode);
        }
    }

    private void failUnsupported(ImportJobEntity job, String mode) {
        job.setStatus("FAILED");
        job.setCompletedAt(OffsetDateTime.now());
        try {
            job.setStats(objectMapper.writeValueAsString(Map.of(
                    "reason", "UNSUPPORTED_SOURCE_MODE",
                    "mode", mode,
                    "detail", "Pull-source execution (" + mode + ") is not implemented; "
                            + "job failed honestly instead of queueing forever. "
                            + "Use POST /v1/import/csv for CSV catalogue loads.")));
        } catch (Exception ignored) {
            job.setStats("{\"reason\":\"UNSUPPORTED_SOURCE_MODE\"}");
        }
        jobRepository.save(job);
        log.warn("Failed source-import job {} (source={}, mode={}): UNSUPPORTED_SOURCE_MODE",
                job.getJobId(), job.getSourceId(), mode);
    }
}
