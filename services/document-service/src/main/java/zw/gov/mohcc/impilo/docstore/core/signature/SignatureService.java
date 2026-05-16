package zw.gov.mohcc.impilo.docstore.core.signature;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.docstore.core.ObjectStorageService;
import zw.gov.mohcc.impilo.docstore.persistence.entity.SignatureJobEntity;
import zw.gov.mohcc.impilo.docstore.persistence.repository.SignatureJobRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Signature orchestration service using pluggable signature providers.
 */
@Service
public class SignatureService {

    private final ObjectStorageService objectStorageService;
    private final SignatureProviderRouter providerRouter;
    private final SignatureJobRepository signatureJobRepository;

    public SignatureService(ObjectStorageService objectStorageService,
                            SignatureProviderRouter providerRouter,
                            SignatureJobRepository signatureJobRepository) {
        this.objectStorageService = objectStorageService;
        this.providerRouter = providerRouter;
        this.signatureJobRepository = signatureJobRepository;
    }

    @Transactional
    public Map<String, Object> requestSignature(UUID objectId, String signerId) {
        TrustContext ctx = TrustContextHolder.require();
        objectStorageService.getObjectEntity(objectId);
        SignatureProvider provider = providerRouter.activeProvider();

        SignatureJobEntity job = new SignatureJobEntity();
        job.setObjectId(objectId);
        job.setTenantId(ctx.tenantId());
        job.setProviderType(provider.providerType());
        job.setStatus("REQUESTED");
        job.setSignerId(signerId);
        job.setRequestedBy(ctx.actorId() != null ? ctx.actorId() : "system");
        job = signatureJobRepository.save(job);

        try {
            SignatureProvider.SignatureResult result = provider.sign(
                    new SignatureProvider.SignatureRequest(objectId, signerId, job.getRequestedBy()));
            job.setStatus("SIGNED");
            job.setSignatureRef(result.signatureRef());
            job.setVerificationUrl(result.verificationUrl());
            job.setSignedAt(result.signedAt());
            job.setCompletedAt(OffsetDateTime.now());
            return toPayload(signatureJobRepository.save(job));
        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            return toPayload(signatureJobRepository.save(job));
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLatestSignature(UUID objectId) {
        SignatureJobEntity job = signatureJobRepository.findTopByObjectIdOrderByRequestedAtDesc(objectId)
                .orElseThrow(() -> new NoSuchElementException("Signature job not found for object: " + objectId));
        return toPayload(job);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verifySignature(UUID jobId) {
        SignatureJobEntity job = signatureJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new NoSuchElementException("Signature job not found: " + jobId));
        boolean verified = "SIGNED".equalsIgnoreCase(job.getStatus())
                && job.getSignatureRef() != null
                && !job.getSignatureRef().isBlank();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", job.getJobId());
        response.put("objectId", job.getObjectId());
        response.put("verified", verified);
        response.put("status", verified ? "VERIFIED" : "NOT_VERIFIED");
        response.put("verificationUrl", job.getVerificationUrl());
        response.put("signatureRef", job.getSignatureRef());
        return response;
    }

    private Map<String, Object> toPayload(SignatureJobEntity job) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getJobId());
        body.put("objectId", job.getObjectId());
        body.put("providerType", job.getProviderType());
        body.put("status", job.getStatus());
        body.put("signerId", job.getSignerId());
        body.put("signatureRef", job.getSignatureRef());
        body.put("verificationUrl", job.getVerificationUrl());
        body.put("signedAt", job.getSignedAt());
        body.put("errorMessage", job.getErrorMessage());
        body.put("requestedAt", job.getRequestedAt());
        body.put("completedAt", job.getCompletedAt());
        body.put("updatedAt", job.getUpdatedAt());
        return body;
    }
}
