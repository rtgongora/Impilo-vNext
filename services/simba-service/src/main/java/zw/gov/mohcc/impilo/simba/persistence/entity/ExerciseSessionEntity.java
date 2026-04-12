package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "exercise_sessions", schema = "simba")
public class ExerciseSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "person_cpid", nullable = false, length = 128)
    private String personCpid;

    @Column(name = "exercise_type", nullable = false, length = 64)
    private String exerciseType;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Column(name = "calories")
    private Integer calories;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "laps")
    private Integer laps;

    @Column(name = "repetitions")
    private Integer repetitions;

    @Column(name = "source", length = 64)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (sessionId == null) {
            sessionId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPersonCpid() {
        return personCpid;
    }

    public void setPersonCpid(String personCpid) {
        this.personCpid = personCpid;
    }

    public String getExerciseType() {
        return exerciseType;
    }

    public void setExerciseType(String exerciseType) {
        this.exerciseType = exerciseType;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(OffsetDateTime startTime) {
        this.startTime = startTime;
    }

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(OffsetDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getCalories() {
        return calories;
    }

    public void setCalories(Integer calories) {
        this.calories = calories;
    }

    public Integer getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(Integer distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public Integer getLaps() {
        return laps;
    }

    public void setLaps(Integer laps) {
        this.laps = laps;
    }

    public Integer getRepetitions() {
        return repetitions;
    }

    public void setRepetitions(Integer repetitions) {
        this.repetitions = repetitions;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
