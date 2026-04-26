package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "zw_admin_district", schema = "tuso")
public class ZwAdminDistrictEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "province_code", nullable = false, length = 16)
    private String provinceCode;

    @Column(name = "district_code", nullable = false, length = 64, unique = true)
    private String districtCode;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "source_ref", length = 512)
    private String sourceRef;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public void setProvinceCode(String provinceCode) {
        this.provinceCode = provinceCode;
    }

    public String getDistrictCode() {
        return districtCode;
    }

    public void setDistrictCode(String districtCode) {
        this.districtCode = districtCode;
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
