package zw.gov.mohcc.impilo.hrpayroll.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_records", schema = "hr")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceRecordEntity {
    @Id
    private UUID recordId;
    @Column(nullable = false)
    private UUID employeeId;
    private UUID facilityId;
    private LocalDate shiftDate;
    private OffsetDateTime clockIn;
    private OffsetDateTime clockOut;
    private BigDecimal hoursWorked;
    private BigDecimal overtimeHours;
    @Column(nullable = false, length = 16)
    private String status;

    @PrePersist
    void pre() {
        if (recordId == null) {
            recordId = UUID.randomUUID();
        }
    }
}
