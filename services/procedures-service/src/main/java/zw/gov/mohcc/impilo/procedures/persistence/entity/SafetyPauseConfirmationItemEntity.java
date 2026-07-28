package zw.gov.mohcc.impilo.procedures.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

/** One confirmation item within a safety-pause template — a row, not a column, for the same
 *  reason procedure_requirement is rows: a sixteenth confirmation item should be a content
 *  release, not a migration. */
@Entity
@Table(name = "safety_pause_confirmation_item", schema = "procedures")
public class SafetyPauseConfirmationItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "confirmation_item", nullable = false, length = 32)
    private String confirmationItem;

    @Column(name = "prompt_text", nullable = false)
    private String promptText;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public UUID getId() { return id; }
    public UUID getTemplateId() { return templateId; }
    public String getConfirmationItem() { return confirmationItem; }
    public String getPromptText() { return promptText; }
    public Integer getDisplayOrder() { return displayOrder; }
}
