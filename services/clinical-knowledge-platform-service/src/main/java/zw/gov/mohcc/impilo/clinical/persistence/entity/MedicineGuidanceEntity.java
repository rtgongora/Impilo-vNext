package zw.gov.mohcc.impilo.clinical.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "clinical", name = "medicine_guidance")
public class MedicineGuidanceEntity {

    @Id
    private UUID id;

    @Column(name = "medicine_name", nullable = false)
    private String medicineName;

    @Column(name = "generic_name")
    private String genericName;

    private String route;
    private String doseExpression;
    private String doseUnit;
    private String frequencyExpression;
    private String durationExpression;

    @Column(name = "level_of_care", length = 8)
    private String levelOfCare;

    @Column(name = "ven_class", length = 8)
    private String venClass;

    private String indication;
    private Integer alternativeRank;
    private Boolean specialistOnly;
    private String notes;

    @Column(name = "source_section_id")
    private UUID sourceSectionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public String getGenericName() {
        return genericName;
    }

    public String getRoute() {
        return route;
    }

    public String getDoseExpression() {
        return doseExpression;
    }

    public String getDoseUnit() {
        return doseUnit;
    }

    public String getFrequencyExpression() {
        return frequencyExpression;
    }

    public String getDurationExpression() {
        return durationExpression;
    }

    public String getLevelOfCare() {
        return levelOfCare;
    }

    public String getVenClass() {
        return venClass;
    }

    public String getIndication() {
        return indication;
    }

    public Boolean getSpecialistOnly() {
        return specialistOnly;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getSourceSectionId() {
        return sourceSectionId;
    }
}
