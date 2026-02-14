package zw.gov.mohcc.impilo.notification.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyRequest;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyResponse;
import zw.gov.mohcc.impilo.notification.service.NotifyService;

@RestController
@RequestMapping("/internal/v1/notify")
public class NotifyController {

    private final NotifyService notifyService;

    public NotifyController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @PostMapping
    public ResponseEntity<NotifyResponse> sendNotification(@Valid @RequestBody NotifyRequest request) {
        RequestContext ctx = RequestContextHolder.require();

        NotifyResponse response = notifyService.send(request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
