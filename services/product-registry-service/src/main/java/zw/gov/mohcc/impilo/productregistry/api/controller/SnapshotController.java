package zw.gov.mohcc.impilo.productregistry.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.productregistry.core.ProductService;
import zw.gov.mohcc.impilo.productregistry.persistence.entity.ProductEntity;

import java.util.List;

/**
 * Snapshot controller for bootstrapping downstream services.
 * Returns all active products in a single response.
 */
@RestController
@RequestMapping("/internal/v1/products/snapshot")
public class SnapshotController {

    private final ProductService productService;

    public SnapshotController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Returns all active products for snapshot/bootstrapping.
     */
    @GetMapping
    public ResponseEntity<List<ProductEntity>> snapshot() {
        List<ProductEntity> activeProducts = productService.getActiveProducts();
        return ResponseEntity.ok(activeProducts);
    }
}
