package zw.gov.mohcc.impilo.simba.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "screening_programmes", schema = "simba")
public class ScreeningProgrammeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "programme_id", nullable = false, unique = true)
    private UUID programmeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "programme_code", nullable = false, length = 64)
    private String programmeCode;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "age_min")
    private Integer ageMin;

    @Column(name = "age_max")
    private Integer ageMax;

    @Column(name = "frequency_months", nullable = false)
    private Integer frequencyMonths = 12;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (programmeId == null) {
            programmeId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getProgrammeId() {
        return programmeId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getProgrammeCode() {
        return programmeCode;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getAgeMin() {
        return ageMin;
    }

    public Integer getAgeMax() {
        return ageMax;
    }

    public Integer getFrequencyMonths() {
        return frequencyMonths;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
