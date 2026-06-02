package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Database-backed language option for native Fundo authoring metadata. */
@Entity
@Table(name = "lrn_fundo_language_option")
public class FundoLanguageOptionEntity {

    @Id
    @Column(name = "code", nullable = false, length = 16)
    private String code;

    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "native_label", length = 128)
    private String nativeLabel;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getNativeLabel() { return nativeLabel; }
    public void setNativeLabel(String nativeLabel) { this.nativeLabel = nativeLabel; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
