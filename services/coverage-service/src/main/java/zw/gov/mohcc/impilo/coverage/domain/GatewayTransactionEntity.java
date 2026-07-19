package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Claims Switch / Payer Gateway transaction (spec §5, §32). Switching != adjudication;
 * a technical ack is never a business ack, and neither is a claim approval.
 */
@Entity
@Table(name = "cv_gateway_transactions")
public class GatewayTransactionEntity {

    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pod_id", nullable = false, length = 64) private String podId = "national-spine";
    @Column(name = "switch_control_number", nullable = false, length = 64) private String switchControlNumber;
    @Column(name = "transaction_type", nullable = false, length = 48) private String transactionType;
    @Column(name = "sender_ref", length = 128) private String senderRef;
    @Column(name = "receiver_payer", length = 255) private String receiverPayer;
    @Column(name = "route", nullable = false, length = 24) private String route;
    @Column(name = "technical_ack", length = 16) private String technicalAck;
    @Column(name = "business_ack", length = 16) private String businessAck;
    @Column(name = "status", nullable = false, length = 24) private String status = "RECEIVED";
    @Column(name = "original_reference", length = 128) private String originalReference;
    @Column(name = "correlation_id") private UUID correlationId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "detail", columnDefinition = "jsonb") private String detail;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    public GatewayTransactionEntity() {}

    public static GatewayTransactionEntity create(UUID tenantId, String podId, String scn, String transactionType,
                                                  String senderRef, String receiverPayer, String route) {
        GatewayTransactionEntity t = new GatewayTransactionEntity();
        t.id = UUID.randomUUID();
        t.tenantId = tenantId;
        t.podId = podId != null ? podId : "national-spine";
        t.switchControlNumber = scn;
        t.transactionType = transactionType;
        t.senderRef = senderRef;
        t.receiverPayer = receiverPayer;
        t.route = route;
        return t;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getSwitchControlNumber() { return switchControlNumber; }
    public String getTransactionType() { return transactionType; }
    public String getSenderRef() { return senderRef; }
    public String getReceiverPayer() { return receiverPayer; }
    public String getRoute() { return route; }
    public void setRoute(String v) { this.route = v; }
    public String getTechnicalAck() { return technicalAck; }
    public void setTechnicalAck(String v) { this.technicalAck = v; this.updatedAt = OffsetDateTime.now(); }
    public String getBusinessAck() { return businessAck; }
    public void setBusinessAck(String v) { this.businessAck = v; this.updatedAt = OffsetDateTime.now(); }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; this.updatedAt = OffsetDateTime.now(); }
    public void setDetail(String v) { this.detail = v; }
    public void setCorrelationId(UUID v) { this.correlationId = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
