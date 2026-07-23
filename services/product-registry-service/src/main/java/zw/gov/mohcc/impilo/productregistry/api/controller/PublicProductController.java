package zw.gov.mohcc.impilo.productregistry.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.productregistry.core.ProductService;
import zw.gov.mohcc.impilo.productregistry.persistence.entity.ProductEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public (anonymous, no-auth) read-only catalog view of the product registry.
 *
 * <p>Endpoints under {@code /v1/public/products} expose an allow-listed projection
 * of {@link ProductEntity} only. Internal fields (tenantId, createdAt) are never
 * emitted; the numeric {@code id} is kept solely as an opaque handle for the detail
 * link. Reads reuse {@link ProductService} methods and, like the internal
 * {@link ProductController}, are global (not tenant-scoped) and returned raw
 * (no ApiResponse envelope).</p>
 */
@RestController
@RequestMapping("/v1/public/products")
public class PublicProductController {

    private static final Logger log = LoggerFactory.getLogger(PublicProductController.class);

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ProductService productService;

    public PublicProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Public product search. Reuses {@link ProductService#searchProducts(String)}
     * (the same method backing {@code ProductController.searchProducts}) and paginates
     * the allow-listed views in memory. Returns an empty list on any read failure.
     */
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchProducts(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "" + DEFAULT_SIZE) int size) {
        try {
            List<ProductEntity> products = productService.searchProducts(q);
            if (products == null || products.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            int safePage = Math.max(page, 0);
            int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
            int from = safePage * safeSize;
            if (from >= products.size()) {
                return ResponseEntity.ok(List.of());
            }
            int to = Math.min(from + safeSize, products.size());

            List<Map<String, Object>> views = new ArrayList<>();
            for (ProductEntity product : products.subList(from, to)) {
                views.add(toPublicView(product));
            }
            return ResponseEntity.ok(views);
        } catch (Exception e) {
            log.warn("Public product search failed for q='{}': {}", q, e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Public single-product detail. Returns 404 when the product is absent or on
     * any read failure.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProduct(@PathVariable Long id) {
        try {
            ProductEntity product = productService.getProduct(id);
            if (product == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(toPublicView(product));
        } catch (Exception e) {
            log.warn("Public product detail lookup failed for id={}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Projects an entity onto the allow-listed public fields only.
     * tenantId and createdAt are deliberately dropped; id is kept as an opaque
     * handle for the detail link.
     */
    private Map<String, Object> toPublicView(ProductEntity product) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", product.getId());
        view.put("productCode", product.getProductCode());
        view.put("name", product.getName());
        view.put("category", product.getCategory());
        view.put("unitOfMeasure", product.getUnitOfMeasure());
        view.put("status", product.getStatus());
        return view;
    }
}
