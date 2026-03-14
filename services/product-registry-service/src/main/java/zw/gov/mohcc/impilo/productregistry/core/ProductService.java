package zw.gov.mohcc.impilo.productregistry.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.productregistry.api.dto.CreateProductRequest;
import zw.gov.mohcc.impilo.productregistry.api.dto.UpdateProductRequest;
import zw.gov.mohcc.impilo.productregistry.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.productregistry.persistence.entity.ProductEntity;
import zw.gov.mohcc.impilo.productregistry.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.productregistry.persistence.repository.ProductRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for product registry CRUD operations.
 * Each mutation appends an event to the outbox for reliable Kafka publishing.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ProductService(ProductRepository productRepository,
                          EventOutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists all products.
     */
    public List<ProductEntity> listProducts() {
        return productRepository.findAll();
    }

    /**
     * Retrieves a single product by its ID.
     */
    public ProductEntity getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + id));
    }

    /**
     * Searches products by name, product code, or category.
     */
    public List<ProductEntity> searchProducts(String query) {
        return productRepository.search(query);
    }

    /**
     * Returns all active products for snapshot/bootstrapping.
     */
    public List<ProductEntity> getActiveProducts() {
        return productRepository.findByStatus("ACTIVE");
    }

    /**
     * Creates a new product and writes a PRODUCT_CREATED outbox event.
     */
    @Transactional
    public ProductEntity createProduct(CreateProductRequest request) {
        ProductEntity product = new ProductEntity();
        product.setTenantId(request.getTenantId());
        product.setProductCode(request.getProductCode());
        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setUnitOfMeasure(request.getUnitOfMeasure());
        product.setStatus("ACTIVE");

        product = productRepository.save(product);

        appendOutboxEvent(product.getTenantId(), "PRODUCT_CREATED", buildProductPayload(product));

        log.info("Product created: id={}, code={}, name={}",
                product.getId(), product.getProductCode(), product.getName());

        return product;
    }

    /**
     * Updates an existing product and writes a PRODUCT_UPDATED outbox event.
     */
    @Transactional
    public ProductEntity updateProduct(Long id, UpdateProductRequest request) {
        ProductEntity product = getProduct(id);

        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setUnitOfMeasure(request.getUnitOfMeasure());
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }

        product = productRepository.save(product);

        appendOutboxEvent(product.getTenantId(), "PRODUCT_UPDATED", buildProductPayload(product));

        log.info("Product updated: id={}, code={}, name={}",
                product.getId(), product.getProductCode(), product.getName());

        return product;
    }

    private Map<String, Object> buildProductPayload(ProductEntity product) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", product.getId());
        payload.put("tenantId", product.getTenantId().toString());
        payload.put("productCode", product.getProductCode());
        payload.put("name", product.getName());
        payload.put("category", product.getCategory());
        payload.put("unitOfMeasure", product.getUnitOfMeasure());
        payload.put("status", product.getStatus());
        payload.put("createdAt", product.getCreatedAt() != null ? product.getCreatedAt().toString() : null);
        return payload;
    }

    private void appendOutboxEvent(UUID tenantId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setTenantId(tenantId);
        outbox.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
        outbox.setCorrelationId(UUID.randomUUID().toString());
        outbox.setEventType(eventType);
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
        outboxRepository.save(outbox);
    }
}
