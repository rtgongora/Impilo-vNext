package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One completeness check against an {@link EmergencyKitEntity}'s manifest (W10). {@code discrepancies}
 * is required whenever {@code complete=false} (chk_iekc_discrepancies_required) — a discrepancy check
 * with no recorded discrepancy detail is not auditable.
 */
@Entity
@Table(name = "inv_emergency_kit_check")
public class EmergencyKitCheckEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "check_id")
    private UUID checkId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "kit_id", nullable = false)
    private UUID kitId;

    @Column(name = "checked_by", nullable = false)
    private String checkedBy;

    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt;

    @Column(name = "complete", nullable = false)
    private boolean complete;

    /** Raw JSON: [{"itemCode","expectedQty","foundQty","note"}, ...]. Required when complete=false. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "discrepancies", columnDefinition = "jsonb")
    private String discrepancies;

    @Column(name = "reseal_ref")
    private String resealRef;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    void onCreate() {
        if (checkedAt == null) checkedAt = OffsetDateTime.now();
    }

    public UUID getCheckId() { return checkId; }
    public void setCheckId(UUID checkId) { this.checkId = checkId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getKitId() { return kitId; }
    public void setKitId(UUID kitId) { this.kitId = kitId; }
    public String getCheckedBy() { return checkedBy; }
    public void setCheckedBy(String checkedBy) { this.checkedBy = checkedBy; }
    public OffsetDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(OffsetDateTime checkedAt) { this.checkedAt = checkedAt; }
    public boolean isComplete() { return complete; }
    public void setComplete(boolean complete) { this.complete = complete; }
    public String getDiscrepancies() { return discrepancies; }
    public void setDiscrepancies(String discrepancies) { this.discrepancies = discrepancies; }
    public String getResealRef() { return resealRef; }
    public void setResealRef(String resealRef) { this.resealRef = resealRef; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
