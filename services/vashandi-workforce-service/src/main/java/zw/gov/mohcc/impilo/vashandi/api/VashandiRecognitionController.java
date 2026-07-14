package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vashandi.core.WorkforceRecognitionService;

/**
 * Workforce recognition read-model keyed by the canonical Health ID.
 *
 * <p>Consumed by the experience BFF recognition composition and by advantage
 * surfaces (COSTA fee exemption, coverage subsidy opt-in). GENERIC RESPONSE:
 * unknown health ids yield the identical {@code recognised=false} body —
 * never a 404 (anti-enumeration, same doctrine as the session-context
 * read-model).</p>
 */
@RestController
@RequestMapping("/v1/internal/vashandi/workforce/by-health-id")
public class VashandiRecognitionController {

    private final WorkforceRecognitionService recognitionService;

    public VashandiRecognitionController(WorkforceRecognitionService recognitionService) {
        this.recognitionService = recognitionService;
    }

    @GetMapping("/{healthId}/recognition")
    public WorkforceRecognitionService.RecognitionResult recognition(@PathVariable String healthId) {
        TrustContext ctx = TrustContextHolder.require();
        return recognitionService.recognitionByHealthId(ctx.tenantId(), healthId);
    }
}
