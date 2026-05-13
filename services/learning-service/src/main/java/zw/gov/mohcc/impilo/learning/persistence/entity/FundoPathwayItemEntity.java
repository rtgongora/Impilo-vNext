package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/** Native Fundo pathway item (Phase 5A). */
@Entity
@Table(
        name = "lrn_fundo_pathway_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_lrn_fundo_pathway_item_seq",
                columnNames = {"pathway_id", "sequence_no"}))
public class FundoPathwayItemEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "pathway_id", nullable = false)
    private UUID pathwayId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    @Column(name = "prerequisite_course_id")
    private UUID prerequisiteCourseId;

    @PrePersist
    void prePersist() { if (id == null) id = UUID.randomUUID(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPathwayId() { return pathwayId; }
    public void setPathwayId(UUID pathwayId) { this.pathwayId = pathwayId; }
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int sequenceNo) { this.sequenceNo = sequenceNo; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public UUID getPrerequisiteCourseId() { return prerequisiteCourseId; }
    public void setPrerequisiteCourseId(UUID prerequisiteCourseId) { this.prerequisiteCourseId = prerequisiteCourseId; }
}
