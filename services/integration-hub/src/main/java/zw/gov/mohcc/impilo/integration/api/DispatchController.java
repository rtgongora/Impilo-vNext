package zw.gov.mohcc.impilo.integration.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.integration.api.dto.DispatchRequest;
import zw.gov.mohcc.impilo.integration.api.dto.DispatchResponse;
import zw.gov.mohcc.impilo.integration.service.DispatchService;

@RestController
@RequestMapping("/internal/v1/dispatch")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping
    public ResponseEntity<DispatchResponse> dispatch(@Valid @RequestBody DispatchRequest request) {
        RequestContext ctx = RequestContextHolder.require();

        DispatchResponse response = dispatchService.dispatch(request, ctx);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
