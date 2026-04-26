package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "zw_admin_ward", schema = "tuso")
public class ZwAdminWardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "district_code", nullable = false, length = 64)
    private String districtCode;

    @Column(name = "ward_code", nullable = false, length = 64)
    private String wardCode;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "source_ref", length = 512)
    private String sourceRef;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public String getDistrictCode() {
        return districtCode;
    }

    public void setDistrictCode(String districtCode) {
        this.districtCode = districtCode;
    }

    public String getWardCode() {
        return wardCode;
    }

    public void setWardCode(String wardCode) {
        this.wardCode = wardCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }
}
