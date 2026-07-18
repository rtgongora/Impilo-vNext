package zw.gov.mohcc.impilo.vito.core.proofing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.vito.core.ProofingMethod;
import zw.gov.mohcc.impilo.vito.core.proofing.engine.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Composes the distinct proofing capabilities (document-integrity, facial-comparison,
 * liveness — doctrine §6) into a single governed decision for a remote document+selfie
 * proofing attempt, and records the outcome into the proofing ledger + assurance vector.
 *
 * <p><b>Native-first, fail-closed:</b> the native MRZ engine can positively decide document
 * integrity, but auto-granting {@link ProofingOutcome#DOCUMENT_VERIFIED} additionally
 * requires that the live face matched and liveness passed. Because the default facial /
 * liveness engines fail closed ({@code UNAVAILABLE}), an unattended remote attempt with a
 * valid document does <b>not</b> auto-verify — it routes to a human review that can raise it
 * to {@link ProofingOutcome#REMOTELY_WITNESSED}/{@link ProofingOutcome#IN_PERSON_WITNESSED}.
 * A document that is actively disproved (failed check digit) is rejected outright. Care is
 * never denied — a rejected/uncertain proofing attempt simply doesn't raise assurance; the
 * assisted/in-person lanes remain open.</p>
 */
@Service
public class ProofingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProofingOrchestrator.class);

    private final ProofingService proofingService;
    private final DocumentVerificationEngine documentEngine;
    private final FacialComparisonEngine facialEngine;
    private final LivenessEngine livenessEngine;
    private final ObjectMapper objectMapper;

    public ProofingOrchestrator(ProofingService proofingService,
                                DocumentVerificationEngine documentEngine,
                                FacialComparisonEngine facialEngine,
                                LivenessEngine livenessEngine,
                                ObjectMapper objectMapper) {
        this.proofingService = proofingService;
        this.documentEngine = documentEngine;
        this.facialEngine = facialEngine;
        this.livenessEngine = livenessEngine;
        this.objectMapper = objectMapper;
    }

    public enum Disposition { VERIFIED, REVIEW_REQUIRED, REJECTED }

    /**
     * @param disposition   the routing decision
     * @param outcome       the assurance outcome granted (null when review-required / rejected)
     * @param assuranceLevel the numeric LOA recorded (0 when none granted)
     * @param signals       the per-capability signals, for the review queue + audit
     * @param reason        an audit-friendly, PII-free summary
     */
    public record ProofingDecision(Disposition disposition,
                                   ProofingOutcome outcome,
                                   int assuranceLevel,
                                   List<ProofingSignal> signals,
                                   String reason) {}

    /**
     * Run a remote document+selfie proofing attempt for {@code healthId}.
     *
     * @param verifierId the acting officer / system id for the ledger (may be a system id for auto attempts)
     */
    public ProofingDecision proveDocumentSelfie(UUID tenantId, UUID healthId,
                                                DocumentEvidence document,
                                                String selfieRef,
                                                String documentFaceRef,
                                                String livenessCaptureRef,
                                                String verifierId) {
        List<ProofingSignal> signals = new ArrayList<>();

        ProofingSignal doc = documentEngine.verifyDocument(document);
        signals.add(doc);

        if (doc.isRejected()) {
            proofingService.recordFailedEvent(tenantId, healthId, ProofingMethod.DOCUMENT,
                    verifierId, "Document proofing rejected: " + doc.summary());
            return new ProofingDecision(Disposition.REJECTED, null, 0, signals,
                    "Presented document failed integrity validation");
        }

        ProofingSignal face = facialEngine.compare(selfieRef, documentFaceRef);
        signals.add(face);
        ProofingSignal liveness = livenessEngine.assess(livenessCaptureRef);
        signals.add(liveness);

        boolean docOk = doc.isVerified();
        boolean faceOk = face.isVerified();
        boolean livenessOk = liveness.isVerified();

        if (docOk && faceOk && livenessOk) {
            ProofingOutcome outcome = ProofingOutcome.DOCUMENT_VERIFIED;
            proofingService.recordEvent(tenantId, healthId, outcome.ledgerMethod(),
                    outcome.assuranceLevel(), artifactJson(document, selfieRef, livenessCaptureRef),
                    verifierId, "Auto document+selfie proofing verified");
            return new ProofingDecision(Disposition.VERIFIED, outcome, outcome.assuranceLevel(), signals,
                    "Document integrity, facial comparison and liveness all confirmed");
        }

        // Native-first reality: with no facial/liveness engine, a valid document alone cannot
        // auto-bind — a human witness must decide. Care is not denied; assurance simply waits.
        log.info("Proofing attempt for health_id={} routed to review (doc={}, face={}, liveness={})",
                healthId, doc.result(), face.result(), liveness.result());
        return new ProofingDecision(Disposition.REVIEW_REQUIRED, null, 0, signals,
                "Document " + doc.result().toLowerCase()
                        + "; facial/liveness could not auto-confirm — routed to assisted review");
    }

    private String artifactJson(DocumentEvidence document, String selfieRef, String livenessCaptureRef) {
        List<String> refs = new ArrayList<>(document == null ? List.of() : document.artifactRefs());
        if (selfieRef != null) {
            refs.add(selfieRef);
        }
        if (livenessCaptureRef != null) {
            refs.add(livenessCaptureRef);
        }
        try {
            return objectMapper.writeValueAsString(refs);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
