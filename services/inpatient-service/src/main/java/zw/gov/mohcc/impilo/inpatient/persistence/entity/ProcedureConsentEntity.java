package zw.gov.mohcc.impilo.inpatient.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reference/index of a required consent type for a procedure episode, pointing at
 * the MVUMO consent request. MVUMO stays the consent SoR; this row only lets
 * startProcedure gate on "all required consents GRANTED, none withdrawn".
 */
@Entity
@Table(name = "procedure_consent", schema = "inpatient")
public class ProcedureConsentEntity {

    public static final String TYPE_PROCEDURE = "PROCEDURE";
    public static final String TYPE_ANAESTHESIA = "ANAESTHESIA";
    public static final String TYPE_TRANSFUSION = "TRANSFUSION";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "consent_type", nullable = false, length = 48)
    private String consentType;

    @Column(name = "mvumo_consent_request_id")
    private UUID mvumoConsentRequestId;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "PENDING";

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    @Column(name = "withdrawn", nullable = false)
    private boolean withdrawn = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("consentType", consentType);
        out.put("mvumoConsentRequestId", mvumoConsentRequestId != null ? mvumoConsentRequestId.toString() : null);
        out.put("status", status);
        out.put("version", version);
        out.put("required", required);
        out.put("withdrawn", withdrawn);
        return out;
    }

    public UUID getId() { return id; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public String getConsentType() { return consentType; }
    public void setConsentType(String consentType) { this.consentType = consentType; }
    public UUID getMvumoConsentRequestId() { return mvumoConsentRequestId; }
    public void setMvumoConsentRequestId(UUID mvumoConsentRequestId) { this.mvumoConsentRequestId = mvumoConsentRequestId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public boolean isWithdrawn() { return withdrawn; }
    public void setWithdrawn(boolean withdrawn) { this.withdrawn = withdrawn; }
}
