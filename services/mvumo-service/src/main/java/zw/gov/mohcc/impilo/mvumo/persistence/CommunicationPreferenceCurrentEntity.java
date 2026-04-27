package zw.gov.mohcc.impilo.mvumo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Materialised "current" preference pointer — updated when a new revision is written. */
@Entity
@Table(name = "communication_preference_current", schema = "mvumo")
@IdClass(CommunicationPreferenceCurrentEntity.CommunicationPreferenceCurrentId.class)
public class CommunicationPreferenceCurrentEntity {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "patient_ref", nullable = false, length = 300)
    private String patientRef;

    @Column(name = "current_revision_id", nullable = false)
    private UUID currentRevisionId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getPatientRef() {
        return patientRef;
    }

    public void setPatientRef(String patientRef) {
        this.patientRef = patientRef;
    }

    public UUID getCurrentRevisionId() {
        return currentRevisionId;
    }

    public void setCurrentRevisionId(UUID currentRevisionId) {
        this.currentRevisionId = currentRevisionId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static class CommunicationPreferenceCurrentId implements Serializable {
        private UUID tenantId;
        private String patientRef;

        public CommunicationPreferenceCurrentId() {}

        public CommunicationPreferenceCurrentId(UUID tenantId, String patientRef) {
            this.tenantId = tenantId;
            this.patientRef = patientRef;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CommunicationPreferenceCurrentId that = (CommunicationPreferenceCurrentId) o;
            return Objects.equals(tenantId, that.tenantId) && Objects.equals(patientRef, that.patientRef);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, patientRef);
        }
    }
}
