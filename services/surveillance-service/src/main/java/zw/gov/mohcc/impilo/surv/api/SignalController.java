package zw.gov.mohcc.impilo.surv.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.surv.core.Severity;
import zw.gov.mohcc.impilo.surv.core.SignalService;
import zw.gov.mohcc.impilo.surv.persistence.entity.SignalEntity;

@RestController
@RequestMapping("/internal/v1/signals")
public class SignalController {

    private static final Logger log = LoggerFactory.getLogger(SignalController.class);

    private final SignalService signalService;

    public SignalController(SignalService signalService) {
        this.signalService = signalService;
    }

    @PostMapping
    public ResponseEntity<?> createSignal(@RequestBody CreateSignalRequest request,
                                          HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Create signal [name={}, eventType={}] correlationId={}",
                request.name(), request.eventType(), ctx.correlationId());

        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("INVALID_REQUEST",
                            "Signal name is required",
                            ctx.requestId(), ctx.correlationId()));
        }

        if (request.eventType() == null || request.eventType().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorEnvelope.of("INVALID_REQUEST",
                            "eventType is required",
                            ctx.requestId(), ctx.correlationId()));
        }

        SignalEntity signal = signalService.createSignal(
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                UUID.fromString(ctx.correlationId()),
                request.name(), request.description(),
                request.eventType(), request.conditionField(),
                request.threshold(), request.windowHours(),
                request.severity());

        return ResponseEntity.status(HttpStatus.CREATED).body(signal);
    }

    @GetMapping
    public ResponseEntity<?> listSignals() {
        RequestContext ctx = RequestContextHolder.require();

        log.info("List signals correlationId={}", ctx.correlationId());

        List<SignalEntity> signals = signalService.listSignals(
                UUID.fromString(ctx.tenantId()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", signals);
        body.put("total_elements", signals.size());
        return ResponseEntity.ok(body);
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
