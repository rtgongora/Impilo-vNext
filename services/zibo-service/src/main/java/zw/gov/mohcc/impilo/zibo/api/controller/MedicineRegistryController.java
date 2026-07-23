package zw.gov.mohcc.impilo.zibo.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.zibo.core.MedicineRegistryService;
import zw.gov.mohcc.impilo.zibo.core.MedicineRegistryService.Medicine;

import java.util.List;

/**
 * OF-B8/OF-D — national medicine registry read API (the ZIBO layer of the
 * three-layer formulary stance). A thin projection over the registered WHO ATC
 * CodeSystem artifact — resolve by code, search by name. Write paths stay on
 * the governed artifact lifecycle ({@code /v1/artifacts} draft→publish);
 * there is no side-channel medicine CRUD.
 */
@RestController
@RequestMapping("/v1/medicines")
public class MedicineRegistryController {

    private final MedicineRegistryService medicineRegistryService;

    public MedicineRegistryController(MedicineRegistryService medicineRegistryService) {
        this.medicineRegistryService = medicineRegistryService;
    }

    /** Search medicines by generic name / display / ATC-code prefix. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Medicine>>> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "20") int limit) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                medicineRegistryService.searchByName(query, limit), correlationId));
    }

    /** Resolve one medicine by its exact ATC code. */
    @GetMapping("/{atcCode}")
    public ResponseEntity<ApiResponse<Medicine>> resolve(@PathVariable String atcCode) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        Medicine medicine = medicineRegistryService.resolveByCode(atcCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Medicine not found in the national registry for ATC code: " + atcCode));
        return ResponseEntity.ok(ApiResponse.ok(medicine, correlationId));
    }
}
