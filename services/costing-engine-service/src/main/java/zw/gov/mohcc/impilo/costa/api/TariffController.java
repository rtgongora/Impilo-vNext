package zw.gov.mohcc.impilo.costa.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.costa.domain.entity.TariffEntity;
import zw.gov.mohcc.impilo.costa.service.TariffService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.List;

@RestController
@RequestMapping("/costa/v1/tariffs")
public class TariffController {

    private final TariffService tariffService;

    public TariffController(TariffService tariffService) {
        this.tariffService = tariffService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TariffEntity>>> listTariffs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        var ctx = TrustContextHolder.require();
        Page<TariffEntity> tariffs = tariffService.listTariffs(ctx.tenantId(), PageRequest.of(page, size));
        PagedResponse<TariffEntity> paged = PagedResponse.of(
                tariffs.getContent(), page, size, tariffs.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, ctx.correlationId().toString()));
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<ApiResponse<TariffEntity>> priceByCode(@PathVariable("code") String code) {
        var ctx = TrustContextHolder.require();
        return tariffService.priceFor(ctx.tenantId(), code)
                .map(t -> ResponseEntity.ok(ApiResponse.ok(t, ctx.correlationId().toString())))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.error("TARIFF_NOT_FOUND", "No active tariff for " + code, 404, ctx.correlationId().toString())));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<List<TariffEntity>>> importCsv(@RequestParam("file") MultipartFile file) {
        var ctx = TrustContextHolder.require();
        try {
            List<TariffEntity> imported = tariffService.importCsv(file.getInputStream());
            return ResponseEntity.ok(ApiResponse.ok(imported, ctx.correlationId().toString()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("IMPORT_FAILED", e.getMessage(), 400, ctx.correlationId().toString()));
        }
    }
}
