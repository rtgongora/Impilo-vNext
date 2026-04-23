package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "lrn_cpd_learning_link")
public class CpdLearningLinkEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "council_ref")
    private Long councilRef;

    @Column(name = "cpd_eligible", nullable = false)
    private boolean cpdEligible;

    @Column(name = "default_credit_value", nullable = false)
    private int defaultCreditValue;

    @Column(name = "requires_review_flag", nullable = false)
    private boolean requiresReviewFlag = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public Long getCouncilRef() {
        return councilRef;
    }

    public void setCouncilRef(Long councilRef) {
        this.councilRef = councilRef;
    }

    public boolean isCpdEligible() {
        return cpdEligible;
    }

    public void setCpdEligible(boolean cpdEligible) {
        this.cpdEligible = cpdEligible;
    }

    public int getDefaultCreditValue() {
        return defaultCreditValue;
    }

    public void setDefaultCreditValue(int defaultCreditValue) {
        this.defaultCreditValue = defaultCreditValue;
    }

    public boolean isRequiresReviewFlag() {
        return requiresReviewFlag;
    }

    public void setRequiresReviewFlag(boolean requiresReviewFlag) {
        this.requiresReviewFlag = requiresReviewFlag;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
