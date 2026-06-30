package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Lightweight daily habit check-in (distinct from quantitative goals). */
@Entity
@Table(name = "habit_check_ins", schema = "simba")
public class HabitCheckInEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "check_in_id", nullable = false, unique = true)
    private UUID checkInId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "person_cpid", nullable = false, length = 128)
    private String personCpid;

    @Column(name = "habit_code", nullable = false, length = 64)
    private String habitCode;

    @Column(name = "goal_id")
    private UUID goalId;

    @Column(name = "enrollment_id")
    private UUID enrollmentId;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "DONE";

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (checkInId == null) {
            checkInId = UUID.randomUUID();
        }
        if (checkInDate == null) {
            checkInDate = LocalDate.now();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getCheckInId() { return checkInId; }
    public void setCheckInId(UUID checkInId) { this.checkInId = checkInId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPersonCpid() { return personCpid; }
    public void setPersonCpid(String personCpid) { this.personCpid = personCpid; }
    public String getHabitCode() { return habitCode; }
    public void setHabitCode(String habitCode) { this.habitCode = habitCode; }
    public UUID getGoalId() { return goalId; }
    public void setGoalId(UUID goalId) { this.goalId = goalId; }
    public UUID getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(UUID enrollmentId) { this.enrollmentId = enrollmentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public void setCheckInDate(LocalDate checkInDate) { this.checkInDate = checkInDate; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
