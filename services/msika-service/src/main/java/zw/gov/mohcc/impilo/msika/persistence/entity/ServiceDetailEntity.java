package zw.gov.mohcc.impilo.msika.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "msika_service_details")
public class ServiceDetailEntity {

    @Id
    @Column(name = "item_id", length = 26)
    private String itemId;

    @Column(name = "service_category", length = 100)
    private String serviceCategory;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "requires_schedule", nullable = false)
    private boolean requiresSchedule = false;

    @Column(name = "requires_referral", nullable = false)
    private boolean requiresReferral = false;

    @Column(name = "facility_level_min", length = 50)
    private String facilityLevelMin;

    @Column(name = "specialty_required", length = 100)
    private String specialtyRequired;

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getServiceCategory() { return serviceCategory; }
    public void setServiceCategory(String serviceCategory) { this.serviceCategory = serviceCategory; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public boolean isRequiresSchedule() { return requiresSchedule; }
    public void setRequiresSchedule(boolean requiresSchedule) { this.requiresSchedule = requiresSchedule; }
    public boolean isRequiresReferral() { return requiresReferral; }
    public void setRequiresReferral(boolean requiresReferral) { this.requiresReferral = requiresReferral; }
    public String getFacilityLevelMin() { return facilityLevelMin; }
    public void setFacilityLevelMin(String facilityLevelMin) { this.facilityLevelMin = facilityLevelMin; }
    public String getSpecialtyRequired() { return specialtyRequired; }
    public void setSpecialtyRequired(String specialtyRequired) { this.specialtyRequired = specialtyRequired; }
}
