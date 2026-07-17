package zw.gov.mohcc.impilo.obs.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * PII-free client-event breadcrumb.
 *
 * <p>Only the allow-listed columns below are ever persisted. Never add
 * free-text message/body columns to this sink.</p>
 */
@Entity
@Table(name = "client_events", schema = "obs")
public class ClientEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "route", length = 256)
    private String route;

    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "source", nullable = false, length = 32)
    private String source = "CLIENT";

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) receivedAt = OffsetDateTime.now();
        if (source == null) source = "CLIENT";
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getRoute() { return route; }
    public void setRoute(String v) { this.route = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String v) { this.correlationId = v; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String v) { this.requestId = v; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer v) { this.httpStatus = v; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime v) { this.occurredAt = v; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime v) { this.receivedAt = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
}
