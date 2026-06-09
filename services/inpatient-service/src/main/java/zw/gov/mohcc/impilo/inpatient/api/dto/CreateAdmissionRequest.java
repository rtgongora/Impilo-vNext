package zw.gov.mohcc.impilo.inpatient.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for creating a new admission.
 */
public class CreateAdmissionRequest {

    @NotNull
    private UUID tenantId;

    @NotNull
    private UUID encounterId;

    @NotBlank
    private String subjectCpid;

    @NotNull
    private UUID facilityId;

    private UUID wardId;

    private UUID bedId;

    private String admittingDiagnosis;

    private String admissionType;

    private String dietOrders;

    private String activityLevel;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getEncounterId() { return encounterId; }
    public void setEncounterId(UUID encounterId) { this.encounterId = encounterId; }

    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getWardId() { return wardId; }
    public void setWardId(UUID wardId) { this.wardId = wardId; }

    public UUID getBedId() { return bedId; }
    public void setBedId(UUID bedId) { this.bedId = bedId; }

    public String getAdmittingDiagnosis() { return admittingDiagnosis; }
    public void setAdmittingDiagnosis(String admittingDiagnosis) { this.admittingDiagnosis = admittingDiagnosis; }

    public String getAdmissionType() { return admissionType; }
    public void setAdmissionType(String admissionType) { this.admissionType = admissionType; }

    public String getDietOrders() { return dietOrders; }
    public void setDietOrders(String dietOrders) { this.dietOrders = dietOrders; }

    public String getActivityLevel() { return activityLevel; }
    public void setActivityLevel(String activityLevel) { this.activityLevel = activityLevel; }
}
