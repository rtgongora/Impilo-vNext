package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.msika.persistence.entity.ExternalSourceEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.ImportJobEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.ExternalSourceRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.ImportJobRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The worker exists to keep the source-import queue honest: sourced PENDING
 * jobs FAIL with UNSUPPORTED_SOURCE_MODE (pull connectors are not built yet),
 * while CSV-upload jobs (no source_id) are never claimed.
 */
@ExtendWith(MockitoExtension.class)
class SourceImportWorkerTest {

    @Mock private ImportJobRepository jobRepository;
    @Mock private ExternalSourceRepository sourceRepository;

    private SourceImportWorker worker;

    @BeforeEach
    void setUp() {
        worker = new SourceImportWorker(jobRepository, sourceRepository, new ObjectMapper());
    }

    private ImportJobEntity job(String id, String sourceId) {
        ImportJobEntity job = new ImportJobEntity();
        job.setJobId(id);
        job.setSourceId(sourceId);
        job.setStatus("PENDING");
        return job;
    }

    @Test
    void pendingSourcedJob_failsWithUnsupportedSourceMode() {
        ImportJobEntity sourced = job("J1", "SRC1");
        ExternalSourceEntity source = new ExternalSourceEntity();
        source.setSourceId("SRC1");
        source.setMode("REST");
        when(jobRepository.findByStatus(eq("PENDING"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sourced)));
        when(sourceRepository.findById("SRC1")).thenReturn(Optional.of(source));

        worker.processPendingSourceJobs();

        ArgumentCaptor<ImportJobEntity> saved = ArgumentCaptor.forClass(ImportJobEntity.class);
        verify(jobRepository).save(saved.capture());
        assertEquals("FAILED", saved.getValue().getStatus());
        assertNotNull(saved.getValue().getCompletedAt());
        assertTrue(saved.getValue().getStats().contains("UNSUPPORTED_SOURCE_MODE"));
        assertTrue(saved.getValue().getStats().contains("REST"));
    }

    @Test
    void pendingCsvUploadJob_withoutSource_isNeverClaimed() {
        ImportJobEntity csvJob = job("J2", null);
        when(jobRepository.findByStatus(eq("PENDING"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(csvJob)));

        worker.processPendingSourceJobs();

        verify(jobRepository, never()).save(any());
        assertEquals("PENDING", csvJob.getStatus());
    }

    @Test
    void missingSourceRow_stillFailsHonestly_withUnknownMode() {
        ImportJobEntity sourced = job("J3", "GONE");
        when(jobRepository.findByStatus(eq("PENDING"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sourced)));
        when(sourceRepository.findById("GONE")).thenReturn(Optional.empty());

        worker.processPendingSourceJobs();

        ArgumentCaptor<ImportJobEntity> saved = ArgumentCaptor.forClass(ImportJobEntity.class);
        verify(jobRepository).save(saved.capture());
        assertEquals("FAILED", saved.getValue().getStatus());
        assertTrue(saved.getValue().getStats().contains("UNKNOWN"));
    }
}
