package zw.gov.mohcc.impilo.procedures.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * One grade on the Clavien-Dindo surgical/procedural complication severity scale — a real,
 * literature-cited standard (Dindo, Demartines, Clavien 2004), not engineering-seed content.
 * Grades a complication by the THERAPY required to treat it, not by apparent clinical severity
 * alone.
 */
@Entity
@Table(name = "clavien_dindo_grade", schema = "procedures")
public class ClavienDindoGradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "grade_code", nullable = false, length = 8)
    private String gradeCode;

    @Column(name = "grade_label", nullable = false, length = 255)
    private String gradeLabel;

    @Column(nullable = false)
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "source_citation", nullable = false)
    private String sourceCitation;

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getGradeCode() { return gradeCode; }
    public String getGradeLabel() { return gradeLabel; }
    public String getDescription() { return description; }
    public Integer getDisplayOrder() { return displayOrder; }
    public String getSourceCitation() { return sourceCitation; }
}
