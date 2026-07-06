package zw.gov.mohcc.impilo.hrpayroll.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "employees", schema = "hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeEntity {
    @Id
    private UUID employeeId;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 32)
    private String staffNumber;
    private String providerId;
    private String cpid;
    @Column(nullable = false)
    private String givenName;
    @Column(nullable = false)
    private String familyName;
    private LocalDate dateOfBirth;
    private String sex;
    private String nationalIdHash;
    private String phone;
    private String email;
    private UUID facilityId;
    private String departmentId;
    private String jobTitle;
    private String cadre;
    @Column(nullable = false, length = 32)
    private String employmentType;
    /**
     * Payroll-financial employment status ONLY (drives payroll runs/contracts).
     * NOT the employment-trust source of record — workforce-governance
     * {@code HscEmployment.employmentStatus} (table {@code wgv_hsc_employment}) is
     * authoritative for trust/eligibility (vashandi eligibility, BFF
     * TrustProfileComposer, Work gating). This field is set manually via
     * {@code POST /internal/v1/hr/employees} and is NOT synced from governance.
     * Do not consume this field for access decisions.
     */
    @Column(nullable = false, length = 16)
    private String employmentStatus = "ACTIVE";
    private LocalDate hireDate;
    private LocalDate terminationDate;
    private String bankAccount;
    private String bankName;
    private String pensionNumber;
    private String taxNumber;
    private OffsetDateTime createdAt;

    @PrePersist
    void pre() {
        if (employeeId == null) {
            employeeId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
