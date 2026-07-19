package zw.gov.mohcc.impilo.vito.core.proofing.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * A5: the real 1:1 facial-comparison engine — replaces the fail-closed default by
 * running the actual pipeline through the core-infra matcher-engine: fetch the live
 * selfie and the document/enrolled portrait, extract a FACE template from each, and
 * compare them. Registered {@code @Primary} so it wins over
 * {@link FacialComparisonEngine.FailClosed} whenever proofing is engine-backed
 * (default on; {@code vito.proofing.facial.engine=failclosed} restores the stub).
 *
 * <p>Honest, care-first fail-closed at every step: a missing ref → INVALID_INPUT; an
 * unfetchable image or an engine that can't produce a template (ONNX face model not
 * loaded — the model is the EXTERNAL_INTEGRATION seam) → UNAVAILABLE, routing the
 * orchestrator to an assisted/in-person path. A face match is only ever asserted from
 * a real engine MATCH; NO_MATCH is a genuine REJECTED, never fabricated either way.</p>
 */
@Component
@Primary
@ConditionalOnProperty(name = "vito.proofing.facial.engine", havingValue = "matcher", matchIfMissing = true)
public class MatcherEngineFacialComparisonEngine implements FacialComparisonEngine {

    private static final Logger log = LoggerFactory.getLogger(MatcherEngineFacialComparisonEngine.class);

    private final ProofingObjectFetcher objectFetcher;
    private final MatcherEngineProofingClient engineClient;

    public MatcherEngineFacialComparisonEngine(ProofingObjectFetcher objectFetcher,
                                               MatcherEngineProofingClient engineClient) {
        this.objectFetcher = objectFetcher;
        this.engineClient = engineClient;
    }

    @Override
    public ProofingSignal compare(String selfieRef, String documentFaceRef) {
        if (selfieRef == null || documentFaceRef == null) {
            return ProofingSignal.invalidInput(CAPABILITY, "Missing selfie or document-face reference");
        }

        byte[] selfie = objectFetcher.fetch(selfieRef);
        byte[] documentFace = objectFetcher.fetch(documentFaceRef);
        if (selfie == null || documentFace == null) {
            return ProofingSignal.unavailable(CAPABILITY,
                    "Could not fetch the selfie or document-face capture — fails closed (no fabricated match).");
        }

        Optional<String> selfieTpl = engineClient.extractFaceTemplate(selfie);
        Optional<String> documentTpl = engineClient.extractFaceTemplate(documentFace);
        if (selfieTpl.isEmpty() || documentTpl.isEmpty()) {
            return ProofingSignal.unavailable(CAPABILITY,
                    "Matcher-engine could not extract a face template (no face model loaded, or no detectable "
                            + "face) — fails closed; route to assisted/in-person.");
        }

        MatcherEngineProofingClient.FaceVerdict verdict =
                engineClient.verifyFace(selfieTpl.get(), documentTpl.get());
        switch (verdict.result()) {
            case "MATCH" -> {
                log.info("Facial comparison MATCH (confidence {})", verdict.confidence());
                return ProofingSignal.verified(CAPABILITY, verdict.confidence(),
                        "Live selfie matched the document/enrolled portrait");
            }
            case "NO_MATCH" -> {
                return ProofingSignal.rejected(CAPABILITY,
                        "Live selfie did not match the document/enrolled portrait");
            }
            default -> {
                return ProofingSignal.unavailable(CAPABILITY,
                        "Matcher-engine returned " + verdict.result() + " — fails closed.");
            }
        }
    }
}
