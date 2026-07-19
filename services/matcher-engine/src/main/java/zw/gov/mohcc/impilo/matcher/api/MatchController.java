package zw.gov.mohcc.impilo.matcher.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.matcher.engine.MatchDispatcher;
import zw.gov.mohcc.impilo.matcher.engine.Modality;
import zw.gov.mohcc.impilo.matcher.engine.ModalityMatcher;

import java.util.Base64;
import java.util.List;

/**
 * Internal engine API — reached only by the abis-service adapter over the cluster
 * network (like Orthanc's REST API behind pacs-adapter). No PII, no Health IDs:
 * requests carry opaque {@code subjectRef} strings and template/sample bytes only.
 *
 * <ul>
 *   <li>{@code POST /v1/engine/extract}  — capture image → template + quality</li>
 *   <li>{@code POST /v1/engine/verify}   — 1:1 probe vs enrolled</li>
 *   <li>{@code POST /v1/engine/identify} — 1:N probe vs gallery (candidates, never a merge)</li>
 *   <li>{@code GET  /v1/engine/capabilities} — which modalities can actually match now</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/engine")
public class MatchController {

    private final MatchDispatcher dispatcher;

    public MatchController(MatchDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @GetMapping("/capabilities")
    public ResponseEntity<Object> capabilities() {
        return ResponseEntity.ok(dispatcher.capabilities());
    }

    @PostMapping("/extract")
    public ResponseEntity<ExtractResponse> extract(@Valid @RequestBody ExtractRequest req) {
        ModalityMatcher.ExtractionResult r = dispatcher.extract(
                req.modality(), b64(req.sampleBase64()), req.width(), req.height(), req.dpi());
        return ResponseEntity.ok(new ExtractResponse(
                r.ok(), r.ok() ? b64(r.template()) : null, r.quality(), r.detail()));
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verify(@Valid @RequestBody VerifyRequest req) {
        ModalityMatcher.VerificationResult r = dispatcher.verify(
                req.modality(), b64(req.probeTemplateBase64()), b64(req.enrolledTemplateBase64()));
        return ResponseEntity.ok(new VerifyResponse(r.result(), r.score(), r.confidence(), r.detail()));
    }

    @PostMapping("/identify")
    public ResponseEntity<IdentifyResponse> identify(@Valid @RequestBody IdentifyRequest req) {
        List<ModalityMatcher.GalleryEntry> gallery = req.gallery() == null ? List.of()
                : req.gallery().stream()
                    .map(g -> new ModalityMatcher.GalleryEntry(g.subjectRef(), b64(g.templateBase64())))
                    .toList();
        List<ModalityMatcher.Candidate> candidates =
                dispatcher.identify(req.modality(), b64(req.probeTemplateBase64()), gallery, req.maxCandidates());
        List<CandidateDto> dtos = candidates.stream()
                .map(c -> new CandidateDto(c.subjectRef(), c.score(), c.confidence()))
                .toList();
        return ResponseEntity.ok(new IdentifyResponse(dtos));
    }

    private static byte[] b64(String s) {
        return (s == null || s.isBlank()) ? new byte[0] : Base64.getDecoder().decode(s);
    }

    private static String b64(byte[] b) {
        return (b == null || b.length == 0) ? null : Base64.getEncoder().encodeToString(b);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────
    public record ExtractRequest(@NotNull Modality modality, @NotNull String sampleBase64,
                                 int width, int height, int dpi) {}
    public record ExtractResponse(boolean ok, String templateBase64, double quality, String detail) {}

    public record VerifyRequest(@NotNull Modality modality, @NotNull String probeTemplateBase64,
                                @NotNull String enrolledTemplateBase64) {}
    public record VerifyResponse(String result, double score, double confidence, String detail) {}

    public record GalleryDto(String subjectRef, String templateBase64) {}
    public record IdentifyRequest(@NotNull Modality modality, @NotNull String probeTemplateBase64,
                                  List<GalleryDto> gallery, int maxCandidates) {}
    public record CandidateDto(String subjectRef, double score, double confidence) {}
    public record IdentifyResponse(List<CandidateDto> candidates) {}
}
