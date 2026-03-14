package zw.gov.mohcc.impilo.notification.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.notification.api.dto.DeliveryReceiptRequest;
import zw.gov.mohcc.impilo.notification.api.dto.DeliveryReceiptResponse;
import zw.gov.mohcc.impilo.notification.service.DeliveryReceiptService;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/delivery-receipts")
public class DeliveryReceiptController {

    private final DeliveryReceiptService deliveryReceiptService;

    public DeliveryReceiptController(DeliveryReceiptService deliveryReceiptService) {
        this.deliveryReceiptService = deliveryReceiptService;
    }

    @PostMapping
    public ResponseEntity<DeliveryReceiptResponse> recordReceipt(
            @Valid @RequestBody DeliveryReceiptRequest request) {
        RequestContext ctx = RequestContextHolder.require();

        DeliveryReceiptResponse response = deliveryReceiptService.recordReceipt(request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping(params = "notificationId")
    public ResponseEntity<List<DeliveryReceiptResponse>> getReceiptsByNotification(
            @RequestParam String notificationId) {
        RequestContext ctx = RequestContextHolder.require();

        List<DeliveryReceiptResponse> receipts = deliveryReceiptService.getReceipts(notificationId, ctx.tenantId());
        return ResponseEntity.ok(receipts);
    }

    @GetMapping(params = "status")
    public ResponseEntity<Page<DeliveryReceiptResponse>> listReceiptsByStatus(
            @RequestParam String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        RequestContext ctx = RequestContextHolder.require();

        Page<DeliveryReceiptResponse> receipts = deliveryReceiptService.listReceipts(ctx.tenantId(), status, page, size);
        return ResponseEntity.ok(receipts);
    }
}
