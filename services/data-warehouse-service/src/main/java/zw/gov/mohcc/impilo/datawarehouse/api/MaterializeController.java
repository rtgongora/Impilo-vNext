package zw.gov.mohcc.impilo.datawarehouse.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.datawarehouse.api.dto.MaterializeRequest;
import zw.gov.mohcc.impilo.datawarehouse.api.dto.MaterializeResponse;
import zw.gov.mohcc.impilo.datawarehouse.core.GoldMaterializerService;

import java.util.UUID;

@RestController
public class MaterializeController {

    private static final Logger log = LoggerFactory.getLogger(MaterializeController.class);
    private final GoldMaterializerService materializerService;

    public MaterializeController(GoldMaterializerService materializerService) {
        this.materializerService = materializerService;
    }

    @PostMapping("/internal/v1/gold/materialize")
    public ResponseEntity<?> materialize(@RequestBody MaterializeRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());

        log.info("Materialize request [eventId={}, eventType={}] correlationId={}",
                request.eventId(), request.eventType(), ctx.correlationId());

        GoldMaterializerService.MaterializationOutcome outcome = materializerService.materializeEvent(
                request.envelopeJson(), tenantId, request.eventId(), request.eventType());
        HttpStatus status = switch (outcome.status()) {
            case "MATERIALIZED", "NO_MAPPED_TARGET" -> HttpStatus.ACCEPTED;
            case "INVALID_ENVELOPE" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.ACCEPTED;
        };
        return ResponseEntity.status(status)
                .body(new MaterializeResponse(request.eventId(), outcome.upsertedCount(), outcome.status()));
    }
}
