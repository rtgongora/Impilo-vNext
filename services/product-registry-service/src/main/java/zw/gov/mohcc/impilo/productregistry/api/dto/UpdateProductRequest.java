package zw.gov.mohcc.impilo.productregistry.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for updating an existing product.
 */
public class UpdateProductRequest {

    @NotBlank
    private String name;

    private String category;

    private String unitOfMeasure;

    private String status;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
