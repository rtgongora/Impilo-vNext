package zw.gov.mohcc.impilo.orgregistry.api.internal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.core.WgvMirrorInventoryService;

/**
 * Read-only inventory of WGV mirror rows for the phase-2a cutover reconciliation
 * report. Paginated; returns {@code {orgRegistryId, sourceRef, code, legalName,
 * status, verificationStatus}} per mirrored organisation.
 *
 * <p>Additive + dormant: this is a NEW read endpoint that does not touch the
 * existing {@code WgvMirrorController} write contract. It is safe to call at any
 * time and returns an empty page while the mirror is empty.
 */
@RestController
@RequestMapping("/internal/v1/org-registry/mirror/wgv")
public class WgvMirrorInventoryController {

    private final WgvMirrorInventoryService inventoryService;

    public WgvMirrorInventoryController(WgvMirrorInventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/inventory")
    public OrgRegistryDtos.MirrorInventoryPage inventory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        return inventoryService.inventory(page, size);
    }
}
