package zw.gov.mohcc.impilo.msika.api.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.api.dto.CreateTariffRequest;
import zw.gov.mohcc.impilo.msika.api.dto.TariffView;
import zw.gov.mohcc.impilo.msika.api.dto.UpdateTariffRequest;
import zw.gov.mohcc.impilo.msika.core.TariffService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

@RestController
@RequestMapping("/internal/v1/tariffs")
public class TariffController {

    private final TariffService tariffService;

    public TariffController(TariffService tariffService) {
        this.tariffService = tariffService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TariffView>> createTariff(@Valid @RequestBody CreateTariffRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        TariffView view = tariffService.toView(tariffService.createTariff(request));
        return ResponseEntity.status(201).body(ApiResponse.ok(view, correlationId));
    }

    @GetMapping("/{tariffId}")
    public ResponseEntity<ApiResponse<TariffView>> getTariff(@PathVariable String tariffId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        TariffView view = tariffService.getTariff(tariffId);
        return ResponseEntity.ok(ApiResponse.ok(view, correlationId));
    }

    @PutMapping("/{tariffId}")
    public ResponseEntity<ApiResponse<TariffView>> updateTariff(
            @PathVariable String tariffId,
            @Valid @RequestBody UpdateTariffRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        TariffView view = tariffService.toView(tariffService.updateTariff(tariffId, request));
        return ResponseEntity.ok(ApiResponse.ok(view, correlationId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TariffView>>> listTariffs(Pageable pageable) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        Page<TariffView> page = tariffService.listTariffs(pageable);
        PagedResponse<TariffView> paged = PagedResponse.of(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }
}
