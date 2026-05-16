package zw.gov.mohcc.impilo.docstore.core.ocr;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.docstore.core.ObjectStorageService;
import zw.gov.mohcc.impilo.docstore.persistence.entity.ObjectEntity;
import zw.gov.mohcc.impilo.docstore.persistence.entity.OcrJobEntity;
import zw.gov.mohcc.impilo.docstore.persistence.repository.OcrJobRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * OCR orchestration service using pluggable OCR providers.
 */
@Service
public class OcrService {

    private final ObjectStorageService objectStorageService;
    private final OcrProviderRouter providerRouter;
    private final OcrJobRepository ocrJobRepository;

    public OcrService(ObjectStorageService objectStorageService,
                      OcrProviderRouter providerRouter,
                      OcrJobRepository ocrJobRepository) {
        this.objectStorageService = objectStorageService;
        this.providerRouter = providerRouter;
        this.ocrJobRepository = ocrJobRepository;
    }

    @Transactional
    public Map<String, Object> requestOcr(UUID objectId) {
        TrustContext ctx = TrustContextHolder.require();
        ObjectEntity object = objectStorageService.getObjectEntity(objectId);
        OcrProvider provider = providerRouter.activeProvider();

        OcrJobEntity job = new OcrJobEntity();
        job.setObjectId(objectId);
        job.setTenantId(ctx.tenantId());
        job.setProviderType(provider.providerType());
        job.setStatus("REQUESTED");
        job.setRequestedBy(ctx.actorId() != null ? ctx.actorId() : "system");
        job = ocrJobRepository.save(job);

        try (InputStream inputStream = objectStorageService.downloadObject(objectId)) {
            byte[] content = inputStream.readAllBytes();
            OcrProvider.OcrResult result = provider.extract(objectId, content, object.getMimeType());
            job.setStatus("COMPLETED");
            job.setExtractedText(result.extractedText());
            job.setConfidence(result.confidence());
            job.setCompletedAt(OffsetDateTime.now());
            return toPayload(ocrJobRepository.save(job));
        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            return toPayload(ocrJobRepository.save(job));
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLatestOcr(UUID objectId) {
        OcrJobEntity job = ocrJobRepository.findTopByObjectIdOrderByRequestedAtDesc(objectId)
                .orElseThrow(() -> new NoSuchElementException("OCR job not found for object: " + objectId));
        return toPayload(job);
    }

    private Map<String, Object> toPayload(OcrJobEntity job) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getJobId());
        body.put("objectId", job.getObjectId());
        body.put("providerType", job.getProviderType());
        body.put("status", job.getStatus());
        body.put("extractedText", job.getExtractedText());
        body.put("confidence", job.getConfidence());
        body.put("errorMessage", job.getErrorMessage());
        body.put("requestedAt", job.getRequestedAt());
        body.put("completedAt", job.getCompletedAt());
        body.put("updatedAt", job.getUpdatedAt());
        return body;
    }
}
