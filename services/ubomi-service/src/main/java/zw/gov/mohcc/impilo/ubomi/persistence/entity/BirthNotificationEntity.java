package zw.gov.mohcc.impilo.ubomi.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "birth_notification", schema = "ubomi")
public class BirthNotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "notification_number", nullable = false)
    private String notificationNumber;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "mother_cpid")
    private String motherCpid;

    @Column(name = "father_cpid")
    private String fatherCpid;

    @Column(name = "child_given_names")
    private String childGivenNames;

    @Column(name = "child_surname")
    private String childSurname;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "time_of_birth")
    private LocalTime timeOfBirth;

    @Column(name = "place_of_birth_type", nullable = false)
    private String placeOfBirthType;

    @Column(name = "sex", nullable = false)
    private String sex;

    @Column(name = "birth_weight_grams")
    private Integer birthWeightGrams;

    @Column(name = "gestational_age_weeks")
    private Integer gestationalAgeWeeks;

    @Column(name = "birth_order")
    private Integer birthOrder;

    @Column(name = "multiple_birth", nullable = false)
    private boolean multipleBirth;

    @Column(name = "attendant_cpid")
    private String attendantCpid;

    @Column(name = "attendant_role")
    private String attendantRole;

    @Column(name = "notified_by_actor", nullable = false)
    private String notifiedByActor;

    @Column(name = "civil_reg_number")
    private String civilRegNumber;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "registered_at")
    private OffsetDateTime registeredAt;

    @JdbcTypeCode(SqlTypes.JSON)


    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (status == null) status = "SUBMITTED";
        if (birthOrder == null) birthOrder = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getNotificationNumber() { return notificationNumber; }
    public void setNotificationNumber(String notificationNumber) { this.notificationNumber = notificationNumber; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getMotherCpid() { return motherCpid; }
    public void setMotherCpid(String motherCpid) { this.motherCpid = motherCpid; }
    public String getFatherCpid() { return fatherCpid; }
    public void setFatherCpid(String fatherCpid) { this.fatherCpid = fatherCpid; }
    public String getChildGivenNames() { return childGivenNames; }
    public void setChildGivenNames(String childGivenNames) { this.childGivenNames = childGivenNames; }
    public String getChildSurname() { return childSurname; }
    public void setChildSurname(String childSurname) { this.childSurname = childSurname; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public LocalTime getTimeOfBirth() { return timeOfBirth; }
    public void setTimeOfBirth(LocalTime timeOfBirth) { this.timeOfBirth = timeOfBirth; }
    public String getPlaceOfBirthType() { return placeOfBirthType; }
    public void setPlaceOfBirthType(String placeOfBirthType) { this.placeOfBirthType = placeOfBirthType; }
    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }
    public Integer getBirthWeightGrams() { return birthWeightGrams; }
    public void setBirthWeightGrams(Integer birthWeightGrams) { this.birthWeightGrams = birthWeightGrams; }
    public Integer getGestationalAgeWeeks() { return gestationalAgeWeeks; }
    public void setGestationalAgeWeeks(Integer gestationalAgeWeeks) { this.gestationalAgeWeeks = gestationalAgeWeeks; }
    public Integer getBirthOrder() { return birthOrder; }
    public void setBirthOrder(Integer birthOrder) { this.birthOrder = birthOrder; }
    public boolean isMultipleBirth() { return multipleBirth; }
    public void setMultipleBirth(boolean multipleBirth) { this.multipleBirth = multipleBirth; }
    public String getAttendantCpid() { return attendantCpid; }
    public void setAttendantCpid(String attendantCpid) { this.attendantCpid = attendantCpid; }
    public String getAttendantRole() { return attendantRole; }
    public void setAttendantRole(String attendantRole) { this.attendantRole = attendantRole; }
    public String getNotifiedByActor() { return notifiedByActor; }
    public void setNotifiedByActor(String notifiedByActor) { this.notifiedByActor = notifiedByActor; }
    public String getCivilRegNumber() { return civilRegNumber; }
    public void setCivilRegNumber(String civilRegNumber) { this.civilRegNumber = civilRegNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public OffsetDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(OffsetDateTime registeredAt) { this.registeredAt = registeredAt; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
