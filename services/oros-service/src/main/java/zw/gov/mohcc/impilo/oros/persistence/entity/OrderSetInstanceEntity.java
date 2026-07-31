package zw.gov.mohcc.impilo.oros.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "oros_order_set_instances")
public class OrderSetInstanceEntity {
    @Id @Column(name = "instance_id") private UUID instanceId;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "set_id") private UUID setId;
    @Column(name = "patient_cpid") private String patientCpid;
    @Column(name = "encounter_ref") private String encounterRef;
    @Column(name = "facility_id") private UUID facilityId;
    @Column(name = "placed_by") private String placedBy;
    @Column(name = "placed_at") private OffsetDateTime placedAt;
    @Column(name = "order_ids_json") private String orderIdsJson;
    @Column(name = "notes") private String notes;

    @PrePersist
    protected void onCreate() {
        if (instanceId == null) instanceId = UUID.randomUUID();
        if (placedAt == null) placedAt = OffsetDateTime.now();
    }

    public UUID getInstanceId() { return instanceId; }
    public void setInstanceId(UUID v) { instanceId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { tenantId = v; }
    public UUID getSetId() { return setId; }
    public void setSetId(UUID v) { setId = v; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String v) { patientCpid = v; }
    public String getEncounterRef() { return encounterRef; }
    public void setEncounterRef(String v) { encounterRef = v; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID v) { facilityId = v; }
    public String getPlacedBy() { return placedBy; }
    public void setPlacedBy(String v) { placedBy = v; }
    public OffsetDateTime getPlacedAt() { return placedAt; }
    public void setPlacedAt(OffsetDateTime v) { placedAt = v; }
    public String getOrderIdsJson() { return orderIdsJson; }
    public void setOrderIdsJson(String v) { orderIdsJson = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
}
