package zw.gov.mohcc.impilo.surv.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.surv.core.Severity;
import zw.gov.mohcc.impilo.surv.core.SignalService;
import zw.gov.mohcc.impilo.surv.persistence.entity.SignalEntity;

@RestController
@RequestMapping("/internal/v1/signals")
public class SignalController {

    private final SignalService signalService;

    public SignalController(SignalService signalService) {
        this.signalService = signalService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SignalEntity>> createSignal(
            @RequestBody CreateSignalRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        SignalEntity signal = signalService.createSignal(
                ctx.tenantId(), ctx.actorId(), ctx.correlationId(),
                request.name(), request.description(),
                request.eventType(), request.conditionField(),
                request.threshold(), request.windowHours(),
                request.severity());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(signal, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SignalEntity>>> listSignals() {
        TrustContext ctx = TrustContextHolder.require();

        List<SignalEntity> signals = signalService.listSignals(ctx.tenantId());

        return ResponseEntity.ok(
                ApiResponse.ok(signals, ctx.correlationId().toString()));
    }

    public record CreateSignalRequest(
            String name,
            String description,
            String eventType,
            String conditionField,
            int threshold,
            int windowHours,
            Severity severity
    ) {}
}
