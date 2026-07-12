package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Curated marketplace sourcing category — the regulator-neutral join point
 * between HPA requirement codes and marketplace listings. Categories only,
 * never suppliers or SKUs.
 */
@Entity
@Table(name = "msika_sourcing_categories")
public class SourcingCategoryEntity {

    @Id
    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "restricted", nullable = false)
    private boolean restricted;

    @Column(name = "required_credential", length = 64)
    private String requiredCredential;

    @Column(name = "search_terms", columnDefinition = "TEXT[]")
    private String[] searchTerms;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "provenance", columnDefinition = "TEXT")
    private String provenance;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isRestricted() { return restricted; }
    public void setRestricted(boolean restricted) { this.restricted = restricted; }
    public String getRequiredCredential() { return requiredCredential; }
    public void setRequiredCredential(String requiredCredential) { this.requiredCredential = requiredCredential; }
    public String[] getSearchTerms() { return searchTerms; }
    public void setSearchTerms(String[] searchTerms) { this.searchTerms = searchTerms; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }
}
