package zw.gov.mohcc.impilo.productregistry.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.productregistry.api.dto.CreateProductRequest;
import zw.gov.mohcc.impilo.productregistry.api.dto.UpdateProductRequest;
import zw.gov.mohcc.impilo.productregistry.core.ProductService;
import zw.gov.mohcc.impilo.productregistry.persistence.entity.ProductEntity;

import java.util.List;

/**
 * REST controller for product registry CRUD and search operations.
 * All endpoints are under {@code /internal/v1/products}.
 */
@RestController
@RequestMapping("/internal/v1/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * List all products.
     */
    @GetMapping
    public ResponseEntity<List<ProductEntity>> listProducts() {
        List<ProductEntity> products = productService.listProducts();
        return ResponseEntity.ok(products);
    }

    /**
     * Get a single product by its ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductEntity> getProduct(@PathVariable Long id) {
        ProductEntity product = productService.getProduct(id);
        return ResponseEntity.ok(product);
    }

    /**
     * Create a new product.
     */
    @PostMapping
    public ResponseEntity<ProductEntity> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        ProductEntity product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    /**
     * Update an existing product.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductEntity> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductEntity product = productService.updateProduct(id, request);
        return ResponseEntity.ok(product);
    }

    /**
     * Search products by name, product code, or category.
     */
    @GetMapping("/search")
    public ResponseEntity<List<ProductEntity>> searchProducts(@RequestParam String q) {
        List<ProductEntity> products = productService.searchProducts(q);
        return ResponseEntity.ok(products);
    }
}
