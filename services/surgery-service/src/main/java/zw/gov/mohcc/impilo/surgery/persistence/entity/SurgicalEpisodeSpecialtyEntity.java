package zw.gov.mohcc.impilo.surgery.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One specialty operating on a surgical episode (V011, surgical demonstration 4).
 *
 * <p>A retroperitoneal sarcoma resection is surgical oncology, vascular and urology in one
 * operation; the single {@code surgical_episode.specialty} column could name only one of them.
 * That column stays as the LEAD — the team that owns the episode and answers for it — and is
 * kept in step with the {@code LEAD} row here.</p>
 */
@Entity
@Table(name = "surgical_episode_specialty", schema = "surgery")
public class SurgicalEpisodeSpecialtyEntity {

    public static final String ROLE_LEAD = "LEAD";
    public static final String ROLE_SHARED = "SHARED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "specialty", nullable = false, length = 48)
    private String specialty;

    @Column(name = "role", nullable = false, length = 16)
    private String role = ROLE_SHARED;

    /** Why this team is on the case: "vascular control of the IVC", "ureteric reimplantation". */
    @Column(name = "contribution", columnDefinition = "text")
    private String contribution;

    @Column(name = "added_by", nullable = false)
    private String addedBy;

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;

    @PrePersist
    void onCreate() {
        if (addedAt == null) addedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID v) { this.episodeId = v; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String v) { this.specialty = v; }
    public String getRole() { return role; }
    public void setRole(String v) { this.role = v; }
    public String getContribution() { return contribution; }
    public void setContribution(String v) { this.contribution = v; }
    public String getAddedBy() { return addedBy; }
    public void setAddedBy(String v) { this.addedBy = v; }
    public OffsetDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(OffsetDateTime v) { this.addedAt = v; }
}
