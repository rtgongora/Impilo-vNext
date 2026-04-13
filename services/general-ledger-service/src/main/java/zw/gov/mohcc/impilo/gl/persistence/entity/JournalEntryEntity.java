package zw.gov.mohcc.impilo.gl.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_entries", schema = "gl")
public class JournalEntryEntity {

    @Id
    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entry_number", nullable = false, length = 32)
    private String entryNumber;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "fiscal_period_id", nullable = false)
    private UUID fiscalPeriodId;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "source_module", nullable = false, length = 32)
    private String sourceModule;

    @Column(name = "source_ref", length = 128)
    private String sourceRef;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "posted_by", length = 128)
    private String postedBy;

    @Column(name = "posted_at")
    private OffsetDateTime postedAt;

    @Column(name = "reversed_by", length = 128)
    private String reversedBy;

    @Column(name = "reversed_at")
    private OffsetDateTime reversedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JournalLineEntity> lines = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (entryId == null) {
            entryId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public void addLine(JournalLineEntity line) {
        line.setJournalEntry(this);
        lines.add(line);
    }

    public UUID getEntryId() {
        return entryId;
    }

    public void setEntryId(UUID entryId) {
        this.entryId = entryId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getEntryNumber() {
        return entryNumber;
    }

    public void setEntryNumber(String entryNumber) {
        this.entryNumber = entryNumber;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public UUID getFiscalPeriodId() {
        return fiscalPeriodId;
    }

    public void setFiscalPeriodId(UUID fiscalPeriodId) {
        this.fiscalPeriodId = fiscalPeriodId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    public void setSourceModule(String sourceModule) {
        this.sourceModule = sourceModule;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPostedBy() {
        return postedBy;
    }

    public void setPostedBy(String postedBy) {
        this.postedBy = postedBy;
    }

    public OffsetDateTime getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(OffsetDateTime postedAt) {
        this.postedAt = postedAt;
    }

    public String getReversedBy() {
        return reversedBy;
    }

    public void setReversedBy(String reversedBy) {
        this.reversedBy = reversedBy;
    }

    public OffsetDateTime getReversedAt() {
        return reversedAt;
    }

    public void setReversedAt(OffsetDateTime reversedAt) {
        this.reversedAt = reversedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<JournalLineEntity> getLines() {
        return lines;
    }

    public void setLines(List<JournalLineEntity> lines) {
        this.lines = lines;
    }
}
