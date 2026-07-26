package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A relative in the family history. Carries no name — a relative is a third party. See V106__structured_history.sql. */
@Entity
@Table(name = "pct_family_history_members")
public class FamilyHistoryMemberEntity {

    @Id
    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "relationship")
    private String relationship;

    @Column(name = "distinguisher")
    private String distinguisher;

    @Column(name = "age")
    private Integer age;

    @Column(name = "deceased")
    private Boolean deceased;

    @Column(name = "deceased_age")
    private Integer deceasedAge;

    @Column(name = "cause_of_death")
    private String causeOfDeath;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (memberId == null) memberId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
    }

    public UUID getMemberId() { return memberId; }
    public void setMemberId(UUID v) { this.memberId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getRelationship() { return relationship; }
    public void setRelationship(String v) { this.relationship = v; }
    public String getDistinguisher() { return distinguisher; }
    public void setDistinguisher(String v) { this.distinguisher = v; }
    public Integer getAge() { return age; }
    public void setAge(Integer v) { this.age = v; }
    public Boolean getDeceased() { return deceased; }
    public void setDeceased(Boolean v) { this.deceased = v; }
    public Integer getDeceasedAge() { return deceasedAge; }
    public void setDeceasedAge(Integer v) { this.deceasedAge = v; }
    public String getCauseOfDeath() { return causeOfDeath; }
    public void setCauseOfDeath(String v) { this.causeOfDeath = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
