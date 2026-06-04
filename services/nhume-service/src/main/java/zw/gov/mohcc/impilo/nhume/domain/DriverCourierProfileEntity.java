package zw.gov.mohcc.impilo.nhume.domain;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nhume_driver_courier_profiles")
public class DriverCourierProfileEntity {

    @Id
    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "courier_code", nullable = false, length = 64)
    private String courierCode;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "courier_kind", nullable = false, length = 48)
    private String courierKind = "COURIER";

    @Column(name = "vito_person_ref", length = 255)
    private String vitoPersonRef;

    @Column(name = "varapi_provider_ref", length = 255)
    private String varapiProviderRef;

    @Column(name = "tuso_facility_ref", length = 255)
    private String tusoFacilityRef;

    @Column(name = "indawo_site_ref", length = 255)
    private String indawoSiteRef;

    @Column(name = "keycloak_subject_id", length = 255)
    private String keycloakSubjectId;

    @Column(name = "organisation_ref", length = 255)
    private String organisationRef;

    @Column(name = "programme_ref", length = 255)
    private String programmeRef;

    @Column(name = "phone", length = 64)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "licence_ref", length = 128)
    private String licenceRef;

    @Column(name = "licence_expiry")
    private LocalDate licenceExpiry;

    @Column(name = "training_status", nullable = false, length = 32)
    private String trainingStatus = "OK";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_zones_json", nullable = false, columnDefinition = "jsonb")
    private String deliveryZonesJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_modes_json", nullable = false, columnDefinition = "jsonb")
    private String allowedModesJson = "[]";

    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;

    @Column(name = "current_status", nullable = false, length = 32)
    private String currentStatus = "OFF_DUTY";

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel = "LOW";

    @Column(name = "trust_verified", nullable = false)
    private boolean trustVerified;

    @Column(name = "council_verified", nullable = false)
    private boolean councilVerified;

    @Column(name = "offline_access_allowed", nullable = false)
    private boolean offlineAccessAllowed;

    @Column(name = "account_expires_at")
    private OffsetDateTime accountExpiresAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public DriverCourierProfileEntity() {}

    public UUID getCourierId() { return courierId; }
    public void setCourierId(UUID v) { this.courierId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getCourierCode() { return courierCode; }
    public void setCourierCode(String v) { this.courierCode = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public String getCourierKind() { return courierKind; }
    public void setCourierKind(String v) { this.courierKind = v; }
    public String getVitoPersonRef() { return vitoPersonRef; }
    public void setVitoPersonRef(String v) { this.vitoPersonRef = v; }
    public String getVarapiProviderRef() { return varapiProviderRef; }
    public void setVarapiProviderRef(String v) { this.varapiProviderRef = v; }
    public String getTusoFacilityRef() { return tusoFacilityRef; }
    public void setTusoFacilityRef(String v) { this.tusoFacilityRef = v; }
    public String getIndawoSiteRef() { return indawoSiteRef; }
    public void setIndawoSiteRef(String v) { this.indawoSiteRef = v; }
    public String getKeycloakSubjectId() { return keycloakSubjectId; }
    public void setKeycloakSubjectId(String v) { this.keycloakSubjectId = v; }
    public String getOrganisationRef() { return organisationRef; }
    public void setOrganisationRef(String v) { this.organisationRef = v; }
    public String getProgrammeRef() { return programmeRef; }
    public void setProgrammeRef(String v) { this.programmeRef = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getLicenceRef() { return licenceRef; }
    public void setLicenceRef(String v) { this.licenceRef = v; }
    public LocalDate getLicenceExpiry() { return licenceExpiry; }
    public void setLicenceExpiry(LocalDate v) { this.licenceExpiry = v; }
    public String getTrainingStatus() { return trainingStatus; }
    public void setTrainingStatus(String v) { this.trainingStatus = v; }
    public String getDeliveryZonesJson() { return deliveryZonesJson; }
    public void setDeliveryZonesJson(String v) { this.deliveryZonesJson = v; }
    public String getAllowedModesJson() { return allowedModesJson; }
    public void setAllowedModesJson(String v) { this.allowedModesJson = v; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String v) { this.deviceFingerprint = v; }
    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String v) { this.currentStatus = v; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String v) { this.riskLevel = v; }
    public boolean isTrustVerified() { return trustVerified; }
    public void setTrustVerified(boolean v) { this.trustVerified = v; }
    public boolean isCouncilVerified() { return councilVerified; }
    public void setCouncilVerified(boolean v) { this.councilVerified = v; }
    public boolean isOfflineAccessAllowed() { return offlineAccessAllowed; }
    public void setOfflineAccessAllowed(boolean v) { this.offlineAccessAllowed = v; }
    public OffsetDateTime getAccountExpiresAt() { return accountExpiresAt; }
    public void setAccountExpiresAt(OffsetDateTime v) { this.accountExpiresAt = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean v) { this.demo = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
