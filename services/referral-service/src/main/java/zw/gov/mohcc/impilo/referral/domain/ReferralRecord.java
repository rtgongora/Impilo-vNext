package zw.gov.mohcc.impilo.referral.domain;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ReferralRecord {

    private final String id;
    private final String patientId;
    private String status;
    private final Map<String, Object> payload;
    private String responseNotes;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private ReferralRecord(String id, String patientId, String status, Map<String, Object> payload) {
        this.id = id;
        this.patientId = patientId;
        this.status = status;
        this.payload = payload;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static ReferralRecord create(String patientId, Map<String, Object> payload) {
        return new ReferralRecord(UUID.randomUUID().toString(), patientId, "PENDING", payload);
    }

    public void accept() {
        if (!"PENDING".equals(status) && !"RESPONDED".equals(status)) {
            throw new IllegalStateException("Referral cannot be accepted from status " + status);
        }
        status = "ACCEPTED";
        touch();
    }

    public void respond(String notes) {
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            throw new IllegalStateException("Referral cannot be responded from status " + status);
        }
        responseNotes = notes;
        status = "RESPONDED";
        touch();
    }

    public void complete() {
        if ("COMPLETED".equals(status)) {
            return;
        }
        status = "COMPLETED";
        touch();
    }

    private void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public Map<String, Object> toEnvelope() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", id);
        envelope.put("patientId", patientId);
        envelope.put("status", status);
        envelope.put("payload", payload);
        envelope.put("responseNotes", responseNotes);
        envelope.put("createdAt", createdAt.toString());
        envelope.put("updatedAt", updatedAt.toString());
        return envelope;
    }

    public String getId() {
        return id;
    }

    public String getPatientId() {
        return patientId;
    }
}
