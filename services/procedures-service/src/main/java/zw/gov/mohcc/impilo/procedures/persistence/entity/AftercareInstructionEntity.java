package zw.gov.mohcc.impilo.procedures.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/** One instruction within an aftercare template — a row, not a column (§17). */
@Entity
@Table(name = "aftercare_instruction", schema = "procedures")
public class AftercareInstructionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "instruction_kind", nullable = false, length = 32)
    private String instructionKind;

    @Column(name = "instruction_text", nullable = false)
    private String instructionText;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public UUID getId() { return id; }
    public UUID getTemplateId() { return templateId; }
    public String getInstructionKind() { return instructionKind; }
    public String getInstructionText() { return instructionText; }
    public Integer getDisplayOrder() { return displayOrder; }
}
